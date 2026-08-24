package dev.identity.clud.security;

import dev.identity.clud.exception.EmailAlreadyExistsException;
import dev.identity.clud.exception.InvalidTokenException;
import dev.identity.clud.jwt.CookieService;
import dev.identity.clud.jwt.JwtProperties;
import dev.identity.clud.jwt.JwtService;
import dev.identity.clud.user.*;
import dev.identity.clud.user.dto.LoginRequestDto;
import dev.identity.clud.user.dto.RegisterRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CookieService cookieService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtProperties jwtProperties;

    /**
     * Регистрация нового пользователя.
     * По умолчанию роль — USER.
     */
    public void register(RegisterRequestDto requestDto) {
        if (userRepository.existsByEmail(requestDto.getEmail())) {
            throw new EmailAlreadyExistsException("Пользователь с таким email уже существует");
        }

        User user = User.builder()
                .email(requestDto.getEmail())
                .password(passwordEncoder.encode(requestDto.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(user);
    }

    /**
     * Вход: проверяем логин/пароль, генерируем пару токенов и кладём в cookie.
     */
    public void login(LoginRequestDto requestDto, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        requestDto.getEmail(),
                        requestDto.getPassword()
                )
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        setTokenCookies(response, userDetails);
    }

    /**
     * Обновление токенов по refresh_token из cookie.
     * Генерируем НОВУЮ пару access + refresh (ротация refresh).
     */
    public void refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieService.getCookieValue(request, CookieService.REFRESH_TOKEN_COOKIE)
                .orElseThrow(() -> new InvalidTokenException("Refresh token не найден"));

        String userEmail;
        try {
            userEmail = jwtService.extractUsername(refreshToken);
        } catch (Exception e) {
            throw new InvalidTokenException("Невалидный refresh token");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

        if (!jwtService.isTokenValid(refreshToken, userDetails)) {
            throw new InvalidTokenException("Refresh token просрочен или невалиден");
        }

        setTokenCookies(response, userDetails);
    }

    /**
     * Выход: удаляем оба cookie.
     */
    public void logout(HttpServletResponse response) {
        cookieService.deleteCookie(response, CookieService.ACCESS_TOKEN_COOKIE);
        cookieService.deleteCookie(response, CookieService.REFRESH_TOKEN_COOKIE);
    }

    /**
     * Вспомогательный метод: создаёт оба токена и пишет их в cookie.
     */
    private void setTokenCookies(HttpServletResponse response, UserDetails userDetails) {
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        cookieService.addTokenCookie(
                response,
                CookieService.ACCESS_TOKEN_COOKIE,
                accessToken,
                jwtProperties.getAccessTokenExpiration()
        );

        cookieService.addTokenCookie(
                response,
                CookieService.REFRESH_TOKEN_COOKIE,
                refreshToken,
                jwtProperties.getRefreshTokenExpiration()
        );
    }
}