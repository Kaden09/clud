package dev.identity.clud.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import dev.identity.clud.error.InvalidTokenException;
import dev.identity.clud.security.principal.AuthenticatedUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final String ISSUER = "clud-identity";
    private static final String AUDIENCE = "clud-api";
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "identity-test-secret-with-at-least-sixty-four-bytes-for-hs384-validation"
                    .getBytes(StandardCharsets.UTF_8));

    private JwtTokenService tokens;
    private SecretKey signingKey;
    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer(ISSUER);
        properties.setAudience(AUDIENCE);
        properties.setAccessTokenExpiration(900_000);
        properties.setRefreshTokenExpiration(604_800_000);
        tokens = new JwtTokenService(properties);
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        user = new AuthenticatedUser(
                UUID.randomUUID(),
                "user@example.com",
                "password-hash",
                true,
                true,
                Instant.now(),
                Instant.now());
    }

    @Test
    void generatesMinimalAccessTokenAndValidatesItsSubject() {
        String token = tokens.generateAccessToken(user);

        assertThat(tokens.validateAccessToken(token)).isEqualTo(user.getId());
        var claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        assertThat(claims).doesNotContainKey("email");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    void generatesRefreshTokenWithJti() {
        var claims = tokens.validateRefreshToken(tokens.generateRefreshToken(user));

        assertThat(claims.userId()).isEqualTo(user.getId());
        assertThat(claims.jti()).isNotBlank();
    }

    @Test
    void rejectsNonHs256AndInvalidRequiredClaims() {
        assertThatThrownBy(() -> tokens.validateAccessToken(token("access", user.getId().toString(),
                UUID.randomUUID().toString(), new Date(), future(), Jwts.SIG.HS384)))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> tokens.validateAccessToken(token("access", "not-a-uuid",
                null, new Date(), future(), Jwts.SIG.HS256)))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> tokens.validateAccessToken(token("refresh", user.getId().toString(),
                UUID.randomUUID().toString(), new Date(), future(), Jwts.SIG.HS256)))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> tokens.validateRefreshToken(token("refresh", user.getId().toString(),
                null, new Date(), future(), Jwts.SIG.HS256)))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> tokens.validateAccessToken(token("access", user.getId().toString(),
                null, null, future(), Jwts.SIG.HS256)))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> tokens.validateAccessToken(token("access", user.getId().toString(),
                null, new Date(), null, Jwts.SIG.HS256)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsInvalidIssuerAudienceSignatureAndExpiration() {
        assertThatThrownBy(() -> tokens.validateAccessToken(contextToken(
                "another-issuer", AUDIENCE, signingKey, future())))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> tokens.validateAccessToken(contextToken(
                ISSUER, "another-api", signingKey, future())))
                .isInstanceOf(InvalidTokenException.class);

        SecretKey anotherKey = Keys.hmacShaKeyFor(
                "another-test-secret-with-at-least-sixty-four-bytes-for-signature-check"
                        .getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> tokens.validateAccessToken(contextToken(
                ISSUER, AUDIENCE, anotherKey, future())))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> tokens.validateAccessToken(contextToken(
                ISSUER, AUDIENCE, signingKey, Date.from(Instant.now().minusSeconds(60)))))
                .isInstanceOf(InvalidTokenException.class);
    }

    private String token(
            String type,
            String subject,
            String jti,
            Date issuedAt,
            Date expiration,
            io.jsonwebtoken.security.MacAlgorithm algorithm) {
        var builder = Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(subject)
                .claim("type", type);
        if (jti != null) {
            builder.id(jti);
        }
        if (issuedAt != null) {
            builder.issuedAt(issuedAt);
        }
        if (expiration != null) {
            builder.expiration(expiration);
        }
        return builder.signWith(signingKey, algorithm).compact();
    }

    private Date future() {
        return Date.from(Instant.now().plusSeconds(300));
    }

    private String contextToken(String issuer, String audience, SecretKey key, Date expiration) {
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(user.getId().toString())
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
