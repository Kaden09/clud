package dev.gateway.clud.ratelimit;

import dev.gateway.clud.config.RateLimitProperties;
import dev.gateway.clud.security.handler.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
    private static final String KEY_PREFIX = "clud:ratelimit:";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String RETRY_AFTER = "Retry-After";
    private static final int TOO_MANY_REQUESTS = 429;

    private final RateLimitProperties properties;
    private final StringRedisTemplate redis;
    private final JwtDecoder jwtDecoder;
    private final SecurityErrorResponseWriter errorWriter;
    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if(!properties.enabled() || matchesAny(request.getRequestURI(), properties.skippedPaths())) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = limitFor(request.getRequestURI());
        String key = KEY_PREFIX + bucketKey(request);
        try {
            Long count = redis.opsForValue().increment(key);
            if(count != null && count == 1) {
                redis.expire(key, Duration.ofMinutes(1));
            }
            if(count == null || count > limit) {
                response.setHeader(RETRY_AFTER, "60");
                errorWriter.write(request, response, TOO_MANY_REQUESTS,
                        "Rate limit exceeded. Try again in 60 seconds.");
            }
        } catch(RuntimeException exception) {
            if(!properties.failOpen()) {
                throw exception;
            }
            log.warn("Rate limiter unavailable, allowing request through: {}", exception.getMessage());
            filterChain.doFilter(request, response);
        }
    }

    private int limitFor(String path) {
        return properties.limits().stream()
                .filter(rule -> matcher.match(rule.path(), path))
                .map(RateLimitProperties.PathLimit::perMinute)
                .findFirst()
                .orElse(properties.defaultLimitPerMinute());
    }

    private String bucketKey(HttpServletRequest request) {
        String userId = authenticatedUserId(request);
        return userId == null ? "ip:" + clientIp(request) : "user:" + userId;
    }

    private String authenticatedUserId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        try {
            Jwt jwt = jwtDecoder.decode(authorization.substring(BEARER_PREFIX.length()));
            return jwt.getSubject();
        }
        catch (RuntimeException exception) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            String first = comma > 0 ? forwardedFor.substring(0, comma) : forwardedFor;
            return first.trim();
        }
        return request.getRemoteAddr();
    }

    private boolean matchesAny(String path, List<String> patterns) {
        return patterns.stream().anyMatch(pattern -> matcher.match(pattern, path));
    }

}
