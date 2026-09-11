package dev.identity.clud.security.jwt;

import java.io.IOException;

import dev.identity.clud.error.InvalidTokenException;
import dev.identity.clud.security.UnknownRouteRequestMatcher;
import dev.identity.clud.security.principal.AuthenticatedUser;
import dev.identity.clud.security.principal.IdentityUserDetailsService;
import dev.identity.clud.security.handler.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UnknownRouteRequestMatcher unknownRoute;
    private final JwtTokenService jwtService;
    private final IdentityUserDetailsService userDetailsService;
    private final SecurityErrorResponseWriter errorWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return unknownRoute.matches(request);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authorization.substring(7);
            var userId = jwtService.extractUserId(token);
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                AuthenticatedUser user = userDetailsService.loadUserById(userId);
                if (!jwtService.isTokenValid(token, user, "access")
                        || !user.isEnabled()
                        || !user.isAccountNonLocked()) {
                    throw new InvalidTokenException("Invalid access token");
                }
                var authentication = new UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        catch (InvalidTokenException | UsernameNotFoundException exception) {
            log.warn("Invalid access token rejected: {}", exception.getMessage());
            SecurityContextHolder.clearContext();
            errorWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid access token");
            return;
        }
        filterChain.doFilter(request, response);
    }

}
