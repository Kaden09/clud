package dev.identity.clud.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import dev.identity.clud.event.UserRegisteredEvent;
import dev.identity.clud.exception.EmailAlreadyExistsException;
import dev.identity.clud.exception.InvalidTokenException;
import dev.identity.clud.jwt.AccessTokenResponse;
import dev.identity.clud.jwt.CookieService;
import dev.identity.clud.jwt.JwtProperties;
import dev.identity.clud.jwt.JwtService;
import dev.identity.clud.refreshSession.RefreshSession;
import dev.identity.clud.refreshSession.RefreshSessionRepository;
import dev.identity.clud.user.User;
import dev.identity.clud.user.UserRepository;
import dev.identity.clud.user.dto.LoginRequestDto;
import dev.identity.clud.user.dto.RegisterRequestDto;
import dev.identity.clud.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CookieService cookieService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtProperties jwtProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UserResponse register(RegisterRequestDto request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .build();
        try {
            userRepository.saveAndFlush(user);
        }
        catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        eventPublisher.publishEvent(UserRegisteredEvent.from(user));
        return UserResponse.from(user);
    }

    @Transactional
    public AccessTokenResponse login(LoginRequestDto request, HttpServletResponse response) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        normalizeEmail(request.getEmail()),
                        request.getPassword()));
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
        createRefreshSession(response, user);
        return new AccessTokenResponse(jwtService.generateAccessToken(user));
    }

    @Transactional
    public AccessTokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieService
                .getCookieValue(request, CookieService.REFRESH_TOKEN_COOKIE)
                .orElseThrow(() -> new InvalidTokenException("Refresh token is required"));

        UUID userId = jwtService.extractUserId(refreshToken);
        CustomUserDetails user;
        try {
            user = userDetailsService.loadUserById(userId);
        }
        catch (UsernameNotFoundException exception) {
            throw new InvalidTokenException("Invalid refresh token");
        }
        if (!jwtService.isTokenValid(refreshToken, user, "refresh")
                || !user.isEnabled()
                || !user.isAccountNonLocked()) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        RefreshSession session = refreshSessionRepository.findByTokenHash(hashToken(refreshToken))
                .orElseThrow(() -> new InvalidTokenException("Refresh session not found"));
        if (session.isRevoked()) {
            refreshSessionRepository.revokeAllByUserId(userId);
            throw new InvalidTokenException("Refresh token reuse detected");
        }
        if (!session.getExpiresAt().isAfter(Instant.now())) {
            throw new InvalidTokenException("Refresh session expired");
        }

        session.setRevoked(true);
        createRefreshSession(response, user);
        return new AccessTokenResponse(jwtService.generateAccessToken(user));
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        cookieService.getCookieValue(request, CookieService.REFRESH_TOKEN_COOKIE)
                .flatMap(token -> refreshSessionRepository.findByTokenHash(hashToken(token)))
                .ifPresent(session -> session.setRevoked(true));
        cookieService.deleteCookie(response, CookieService.REFRESH_TOKEN_COOKIE);
    }

    private void createRefreshSession(HttpServletResponse response, CustomUserDetails user) {
        String token = jwtService.generateRefreshToken(user);
        RefreshSession session = RefreshSession.builder()
                .userId(user.getId())
                .tokenHash(hashToken(token))
                .jti(jwtService.extractJti(token))
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiration()))
                .build();
        refreshSessionRepository.save(session);
        cookieService.addTokenCookie(
                response,
                CookieService.REFRESH_TOKEN_COOKIE,
                token,
                jwtProperties.getRefreshTokenExpiration());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
