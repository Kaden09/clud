package dev.identity.clud.jwt;

import java.io.IOException;

import dev.identity.clud.exception.InvalidTokenException;
import dev.identity.clud.security.CustomUserDetails;
import dev.identity.clud.security.CustomUserDetailsService;
import dev.identity.clud.security.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final SecurityErrorResponseWriter errorWriter;

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
            UUIDHolder userId = new UUIDHolder(jwtService.extractUserId(token));
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                CustomUserDetails user = userDetailsService.loadUserById(userId.value());
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
            filterChain.doFilter(request, response);
        }
        catch (InvalidTokenException | UsernameNotFoundException exception) {
            SecurityContextHolder.clearContext();
            errorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "INVALID_ACCESS_TOKEN", exception.getMessage());
        }
    }

    private record UUIDHolder(java.util.UUID value) {
    }
}
