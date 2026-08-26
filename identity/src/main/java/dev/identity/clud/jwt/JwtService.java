package dev.identity.clud.jwt;

import dev.identity.clud.exception.InvalidTokenException;
import dev.identity.clud.security.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    public String generateAccessToken(CustomUserDetails userDetails) {
        return buildToken(userDetails, jwtProperties.getAccessTokenExpiration(), "access", null);
    }

    public String generateRefreshToken(CustomUserDetails userDetails) {
        String jti = UUID.randomUUID().toString();
        return buildToken(userDetails, jwtProperties.getRefreshTokenExpiration(), "refresh", jti);
    }

    private String buildToken(CustomUserDetails userDetails, long expiration, String tokenType, String jti) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        var builder = Jwts.builder()
                .subject(userDetails.getId().toString())
                .claim("email", userDetails.getEmail())
                .claim("role", roles)
                .claim("type", tokenType)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), Jwts.SIG.HS256);

        if (jti != null) {
            builder.id(jti);
        }

        return builder.compact();
    }

    public boolean isTokenValid(String token, CustomUserDetails userDetails, String expectedType) {
        final String userId = extractSubject(token);
        final String type = extractClaim(token, claims -> claims.get("type", String.class));
        return userId.equals(userDetails.getId().toString())
                && expectedType.equals(type)
                && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractSubject(token));
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("Token expired");
        } catch (JwtException e) {
            throw new InvalidTokenException("Invalid token: " + e.getMessage());
        }
    }
}

