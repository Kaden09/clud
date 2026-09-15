package dev.gateway.clud.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.time.Instant;

import dev.gateway.clud.config.RateLimitProperties;
import dev.gateway.clud.security.handler.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

@SuppressWarnings("unchecked")
class RateLimitFilterTests {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final SecurityErrorResponseWriter errorWriter =
            new SecurityErrorResponseWriter(JsonMapper.builder().build());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void stopsFilterChainWhenLimitIsExceeded() throws Exception {
        RateLimitFilter filter = filter(true);
        MockHttpServletRequest request = request("/api/identity/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("60000"))).thenReturn("11:42001");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("43");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("43");
        assertThat(response.getContentAsString()).contains("\"code\":\"TOO_MANY_REQUESTS\"");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void invokesFilterChainOnceWhenRedisFailsOpen() throws Exception {
        RateLimitFilter filter = filter(true);
        MockHttpServletRequest request = request("/api/files/nodes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("60000")))
                .thenThrow(new RedisConnectionFailureException("offline"));

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void rejectsWithServiceUnavailableWhenRedisFailsClosed() throws Exception {
        RateLimitFilter filter = filter(false);
        MockHttpServletRequest request = request("/api/files/nodes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("60000")))
                .thenThrow(new RedisConnectionFailureException("offline"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doesNotRetryDownstreamWhenItThrowsRuntimeException() throws Exception {
        RateLimitFilter filter = filter(true);
        MockHttpServletRequest request = request("/api/files/nodes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("60000"))).thenReturn("1:60000");
        org.mockito.Mockito.doThrow(new IllegalStateException("downstream failure"))
                .when(chain).doFilter(request, response);

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("downstream failure");
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void usesSeparateRuleBucketsAndClosestForwardedAddress() throws Exception {
        RateLimitFilter filter = filter(true);
        FilterChain chain = mock(FilterChain.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("60000"))).thenReturn("1:60000");

        MockHttpServletRequest login = request("/api/identity/auth/login");
        login.addHeader("X-Forwarded-For", "spoofed-address, 203.0.113.9");
        filter.doFilter(login, new MockHttpServletResponse(), chain);
        filter.doFilter(request("/api/sharing/public/token"), new MockHttpServletResponse(), chain);

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis, times(2)).execute(any(RedisScript.class), keys.capture(), eq("60000"));
        assertThat(keys.getAllValues())
                .containsExactly(
                        List.of("clud:ratelimit:rule:/api/identity/auth/**:ip:203.0.113.9"),
                        List.of("clud:ratelimit:rule:/api/sharing/public/**:ip:127.0.0.1"));
    }

    @Test
    void usesAuthenticatedUserBucket() throws Exception {
        RateLimitFilter filter = filter(true);
        FilterChain chain = mock(FilterChain.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("60000"))).thenReturn("1:60000");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("7b22152f-93c5-48f1-b1c8-a60647fb7d86")
                .issuedAt(Instant.now().minusSeconds(1))
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        filter.doFilter(request("/api/files/nodes"), new MockHttpServletResponse(), chain);

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), eq("60000"));
        assertThat(keys.getValue()).containsExactly(
                "clud:ratelimit:default:user:7b22152f-93c5-48f1-b1c8-a60647fb7d86");
    }

    @Test
    void skipsCorsPreflight() throws Exception {
        RateLimitFilter filter = filter(true);
        MockHttpServletRequest request = request("/api/files/nodes");
        request.setMethod("OPTIONS");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(redis, never()).execute(any(RedisScript.class), anyList(), any());
        verify(chain).doFilter(request, response);
    }

    private RateLimitFilter filter(boolean failOpen) {
        RateLimitProperties properties = new RateLimitProperties(
                true,
                failOpen,
                60,
                List.of(
                        new RateLimitProperties.PathLimit("/api/identity/auth/**", 10),
                        new RateLimitProperties.PathLimit("/api/sharing/public/**", 30)),
                List.of("/actuator/**"));
        return new RateLimitFilter(properties, redis, errorWriter);
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
