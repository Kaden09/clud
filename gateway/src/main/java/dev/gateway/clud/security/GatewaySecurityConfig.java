package dev.gateway.clud.security;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import dev.gateway.clud.config.RateLimitProperties;
import jakarta.servlet.DispatcherType;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import dev.gateway.clud.security.handler.RestAccessDeniedHandler;
import dev.gateway.clud.security.handler.RestAuthenticationEntryPoint;
import dev.gateway.clud.security.jwt.GatewayJwtProperties;
import dev.gateway.clud.security.jwt.TrustedUserHeaderFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({GatewayJwtProperties.class, GatewayCorsProperties.class, RateLimitProperties.class})
public class GatewaySecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TrustedUserHeaderFilter trustedUserHeaderFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/identity/auth/**",
                                "/api/sharing/public/**",
                                "/api/identity/v3/api-docs/**",
                                "/api/files/v3/api-docs/**",
                                "/api/sharing/v3/api-docs/**",
                                "/v3/api-docs/**",
                                "/docs/**",
                                "/swagger-ui/**",
                                "/actuator/health/**",
                                "/actuator/prometheus/**").permitAll()
                        .requestMatchers("/api/files/internal/**").denyAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(trustedUserHeaderFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(GatewayJwtProperties properties) {
        SecretKey signingKey = new SecretKeySpec(
                Base64.getDecoder().decode(properties.getSecret().trim()),
                "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> accessTokenValidator = token -> {
            if (!"access".equals(token.getClaimAsString("type"))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        "Only access tokens are accepted",
                        null));
            }
            String subject = token.getSubject();
            if (subject == null || subject.isBlank()) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        "Token subject must contain a user ID",
                        null));
            }
            try {
                UUID.fromString(subject);
                return OAuth2TokenValidatorResult.success();
            }
            catch (IllegalArgumentException exception) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        "Token subject must contain a valid user ID",
                        null));
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                accessTokenValidator));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewayCorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-ID"));
        configuration.setExposedHeaders(List.of("X-Request-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
