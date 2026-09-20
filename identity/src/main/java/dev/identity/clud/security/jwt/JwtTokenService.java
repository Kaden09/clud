package dev.identity.clud.security.jwt;

import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import dev.identity.clud.error.InvalidTokenException;
import dev.identity.clud.security.principal.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret()));
        this.parser = Jwts.parser()
                .requireIssuer(properties.getIssuer())
                .requireAudience(properties.getAudience())
                .verifyWith(signingKey)
                .sig().clear().add(Jwts.SIG.HS256).and()
                .build();
    }

    public String generateAccessToken(AuthenticatedUser user) {
        return buildToken(user, properties.getAccessTokenExpiration(), "access", null);
    }

    public String generateRefreshToken(AuthenticatedUser user) {
        return buildToken(user, properties.getRefreshTokenExpiration(), "refresh", UUID.randomUUID().toString());
    }

    public UUID validateAccessToken(String token) {
        return validateToken(token, "access", false).userId();
    }

    public RefreshTokenClaims validateRefreshToken(String token) {
        TokenClaims claims = validateToken(token, "refresh", true);
        return new RefreshTokenClaims(claims.userId(), claims.jti());
    }

    private String buildToken(AuthenticatedUser user, long expiration, String type, String jti) {
        var builder = Jwts.builder()
                .issuer(properties.getIssuer())
                .audience().add(properties.getAudience()).and()
                .subject(user.getId().toString())
                .claim("type", type)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey, Jwts.SIG.HS256);
        if (jti != null) {
            builder.id(jti);
        }
        return builder.compact();
    }

    private TokenClaims validateToken(String token, String expectedType, boolean requireJti) {
        try {
            Claims claims = parser
                    .parseSignedClaims(token)
                    .getPayload();
            String subject = claims.getSubject();
            String type = claims.get("type", String.class);
            Date issuedAt = claims.getIssuedAt();
            Date expiration = claims.getExpiration();
            String jti = claims.getId();
            if (subject == null || subject.isBlank()
                    || !expectedType.equals(type)
                    || issuedAt == null
                    || issuedAt.after(new Date())
                    || expiration == null
                    || !expiration.after(new Date())
                    || requireJti && (jti == null || jti.isBlank())) {
                throw new InvalidTokenException("Invalid token claims");
            }
            return new TokenClaims(UUID.fromString(subject), jti);
        }
        catch (ExpiredJwtException exception) {
            throw new InvalidTokenException("Token expired");
        }
        catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException("Invalid token");
        }
    }

    public record RefreshTokenClaims(UUID userId, String jti) {
    }

    private record TokenClaims(UUID userId, String jti) {
    }
}
