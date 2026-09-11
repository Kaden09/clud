package dev.identity.clud.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import dev.identity.clud.event.UserRegisteredEvent;
import dev.identity.clud.security.principal.AuthenticatedUser;
import dev.identity.clud.security.principal.IdentityUserDetailsService;
import dev.identity.clud.error.EmailAlreadyExistsException;
import dev.identity.clud.error.InvalidTokenException;
import dev.identity.clud.auth.dto.AccessTokenResponse;
import dev.identity.clud.session.RefreshCookieService;
import dev.identity.clud.security.jwt.JwtProperties;
import dev.identity.clud.security.jwt.JwtTokenService;
import dev.identity.clud.session.RefreshSession;
import dev.identity.clud.session.RefreshSessionRepository;
import dev.identity.clud.session.RefreshSessionRevocationService;
import dev.identity.clud.user.User;
import dev.identity.clud.user.UserRepository;
import dev.identity.clud.auth.dto.LoginRequest;
import dev.identity.clud.auth.dto.RegisterRequest;
import dev.identity.clud.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final RefreshSessionRevocationService revocationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtService;
    private final RefreshCookieService cookieService;
    private final AuthenticationManager authenticationManager;
    private final IdentityUserDetailsService userDetailsService;
    private final JwtProperties jwtProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .build();
        try {
            userRepository.saveAndFlush(user);
        }
        catch (DataIntegrityViolationException exception) {
            log.warn("Registration failed: email already exists");
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        log.info("User registered successfully: userId={}", user.getId());
        eventPublisher.publishEvent(UserRegisteredEvent.from(user));
        return UserResponse.from(user);
    }

    @Transactional
    public AccessTokenResponse login(LoginRequest request, HttpServletResponse response) {
        String email = normalizeEmail(request.email());
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        email,
                        request.password()));
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        log.info("User logged in successfully: userId={}", user.getId());
        createRefreshSession(response, user);
        return new AccessTokenResponse(jwtService.generateAccessToken(user));
    }

    @Transactional
    public AccessTokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieService
                .getCookieValue(request, RefreshCookieService.REFRESH_TOKEN_COOKIE)
                .orElseThrow(() -> new InvalidTokenException("Refresh token is required"));

        UUID userId = jwtService.extractUserId(refreshToken);
        AuthenticatedUser user;
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
            revocationService.revokeAll(userId);
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
        cookieService.getCookieValue(request, RefreshCookieService.REFRESH_TOKEN_COOKIE)
                .flatMap(token -> refreshSessionRepository.findByTokenHash(hashToken(token)))
                .ifPresent(session -> session.setRevoked(true));
        cookieService.deleteCookie(response, RefreshCookieService.REFRESH_TOKEN_COOKIE);
    }

    private void createRefreshSession(HttpServletResponse response, AuthenticatedUser user) {
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
                RefreshCookieService.REFRESH_TOKEN_COOKIE,
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
