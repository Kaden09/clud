package dev.identity.clud.security.jwt;

import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

import javax.crypto.SecretKey;

import dev.identity.clud.error.InvalidTokenException;
import dev.identity.clud.security.principal.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret()));
    }

    public String generateAccessToken(CustomUserDetails user) {
        return buildToken(user, properties.getAccessTokenExpiration(), "access", null);
    }

    public String generateRefreshToken(CustomUserDetails user) {
        return buildToken(user, properties.getRefreshTokenExpiration(), "refresh", UUID.randomUUID().toString());
    }

    public boolean isTokenValid(String token, CustomUserDetails user, String expectedType) {
        Claims claims = extractAllClaims(token);
        return user.getId().toString().equals(claims.getSubject())
                && expectedType.equals(claims.get("type", String.class))
                && claims.getExpiration().after(new Date());
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaim(token, Claims::getSubject));
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    private String buildToken(CustomUserDetails user, long expiration, String type, String jti) {
        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("type", type)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey, Jwts.SIG.HS256);
        if (jti != null) {
            builder.id(jti);
        }
        return builder.compact();
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        }
        catch (ExpiredJwtException exception) {
            throw new InvalidTokenException("Token expired");
        }
        catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException("Invalid token");
        }
    }
}
