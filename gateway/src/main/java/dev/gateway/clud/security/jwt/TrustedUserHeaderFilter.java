package dev.gateway.clud.security.jwt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class TrustedUserHeaderFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-ID";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String trustedUserId = null;
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication
                && authentication.isAuthenticated()) {
            trustedUserId = authentication.getToken().getSubject();
            log.info("Trusted user header set for userId={}", trustedUserId);
        }

        filterChain.doFilter(new TrustedUserHeaderRequest(request, trustedUserId), response);
    }

    private static final class TrustedUserHeaderRequest extends HttpServletRequestWrapper {

        private final String trustedUserId;

        private TrustedUserHeaderRequest(HttpServletRequest request, String trustedUserId) {
            super(request);
            this.trustedUserId = trustedUserId;
        }

        @Override
        public String getHeader(String name) {
            return USER_ID_HEADER.equalsIgnoreCase(name) ? trustedUserId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (!USER_ID_HEADER.equalsIgnoreCase(name)) {
                return super.getHeaders(name);
            }
            return trustedUserId == null
                    ? Collections.emptyEnumeration()
                    : Collections.enumeration(List.of(trustedUserId));
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            Enumeration<String> originalNames = super.getHeaderNames();
            if (originalNames != null) {
                while (originalNames.hasMoreElements()) {
                    String name = originalNames.nextElement();
                    if (!USER_ID_HEADER.equalsIgnoreCase(name)) {
                        names.add(name);
                    }
                }
            }
            if (trustedUserId != null) {
                names.add(USER_ID_HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}
