package dev.gateway.clud.ratelimit;

import dev.gateway.clud.config.RateLimitProperties;
import dev.gateway.clud.security.handler.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
    private static final String KEY_PREFIX = "clud:ratelimit:";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String RETRY_AFTER = "Retry-After";
    private static final String RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    private static final String RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    private static final String RATE_LIMIT_RESET = "X-RateLimit-Reset";
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVICE_UNAVAILABLE = 503;
    private static final DefaultRedisScript<String> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
                ttl = tonumber(ARGV[1])
            end
            return tostring(count) .. ':' .. tostring(ttl)
            """, String.class);

    private final RateLimitProperties properties;
    private final StringRedisTemplate redis;
    private final SecurityErrorResponseWriter errorWriter;
    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.enabled()
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || matchesAny(request.getRequestURI(), properties.skippedPaths())) {
            filterChain.doFilter(request, response);
            return;
        }

        Limit limit = limitFor(request.getRequestURI());
        String key = KEY_PREFIX + limit.scope() + ":" + bucketKey(request);
        WindowState state;
        try {
            state = increment(key);
        }
        catch (RuntimeException exception) {
            if (properties.failOpen()) {
                log.warn("Rate limiter unavailable, allowing request through: {}", exception.getMessage());
                filterChain.doFilter(request, response);
                return;
            }
            log.error("Rate limiter unavailable, rejecting request", exception);
            errorWriter.write(response, SERVICE_UNAVAILABLE,
                    "Rate limiter is temporarily unavailable. Try again later.");
            return;
        }

        long resetSeconds = Math.max(1, (state.ttlMilliseconds() + 999) / 1000);
        response.setHeader(RATE_LIMIT_LIMIT, Integer.toString(limit.maxRequests()));
        response.setHeader(RATE_LIMIT_REMAINING,
                Long.toString(Math.max(0, limit.maxRequests() - state.count())));
        response.setHeader(RATE_LIMIT_RESET, Long.toString(resetSeconds));

        if (state.count() > limit.maxRequests()) {
            response.setHeader(RETRY_AFTER, Long.toString(resetSeconds));
            errorWriter.write(response, TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Try again in " + resetSeconds + " seconds.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private WindowState increment(String key) {
        String result = redis.execute(INCREMENT_SCRIPT, List.of(key), Long.toString(WINDOW.toMillis()));
        if (result == null) {
            throw new IllegalStateException("Redis returned no rate-limit result");
        }
        int separator = result.indexOf(':');
        if (separator < 1 || separator == result.length() - 1) {
            throw new IllegalStateException("Redis returned an invalid rate-limit result");
        }
        return new WindowState(
                Long.parseLong(result.substring(0, separator)),
                Long.parseLong(result.substring(separator + 1)));
    }

    private Limit limitFor(String path) {
        return properties.limits().stream()
                .filter(rule -> matcher.match(rule.path(), path))
                .map(rule -> new Limit("rule:" + rule.path(), rule.perMinute()))
                .findFirst()
                .orElseGet(() -> new Limit("default", properties.defaultLimitPerMinute()));
    }

    private String bucketKey(HttpServletRequest request) {
        String userId = authenticatedUserId();
        return userId == null ? "ip:" + clientIp(request) : "user:" + userId;
    }

    private String authenticatedUserId() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication
                && authentication.isAuthenticated()) {
            return authentication.getToken().getSubject();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.lastIndexOf(',');
            String closestAddress = comma >= 0 ? forwardedFor.substring(comma + 1) : forwardedFor;
            return closestAddress.trim();
        }
        return request.getRemoteAddr();
    }

    private boolean matchesAny(String path, List<String> patterns) {
        return patterns.stream().anyMatch(pattern -> matcher.match(pattern, path));
    }

    private record Limit(String scope, int maxRequests) {}

    private record WindowState(long count, long ttlMilliseconds) {}
}
