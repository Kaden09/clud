package dev.identity.clud.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/** Lets MVC return 404 without making registered controller endpoints public. */
@Component
public class UnknownRouteRequestMatcher implements RequestMatcher {

    private final ObjectProvider<RequestMappingHandlerMapping> mappings;

    public UnknownRouteRequestMatcher(
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> mappings) {
        this.mappings = mappings;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        var path = ServletRequestPathUtils.parseAndCache(request).pathWithinApplication();
        // Actuator has a separate handler mapping; retain its existing security policy.
        if (path.value().equals("/actuator") || path.value().startsWith("/actuator/")) {
            return false;
        }
        for (var mapping : mappings.getObject().getHandlerMethods().keySet()) {
            var condition = mapping.getPathPatternsCondition();
            // A custom mapping strategy must never accidentally expose a controller.
            if (condition == null || condition.getPatterns().stream().anyMatch(pattern -> pattern.matches(path))) {
                return false;
            }
        }
        // Match paths regardless of HTTP method so an unsupported method cannot bypass authentication.
        return true;
    }
}
