package dev.identity.clud.jwt;

import dev.identity.clud.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final CookieService cookieService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Пытаемся достать access_token из cookie
        Optional<String> tokenOpt = cookieService.getCookieValue(request, CookieService.ACCESS_TOKEN_COOKIE);

        if (tokenOpt.isEmpty()) {
            // Нет токена — просто пропускаем дальше (Spring Security сам решит, пускать или нет)
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = tokenOpt.get();
        String userEmail;

        // 2. Извлекаем email из токена
        try {
            userEmail = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            // Токен кривой — пропускаем
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Если пользователь ещё не аутентифицирован в текущем запросе
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Загружаем пользователя из БД
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            // 4. Проверяем валидность токена
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // Создаём объект аутентификации
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null, // credentials не нужны
                        userDetails.getAuthorities()
                );

                // Добавляем детали запроса (IP, сессия и т.д.)
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Устанавливаем аутентификацию в контекст безопасности
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // 5. Передаём запрос дальше по цепочке фильтров
        filterChain.doFilter(request, response);
    }
}