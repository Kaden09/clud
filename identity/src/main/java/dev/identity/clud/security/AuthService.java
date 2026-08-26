package dev.identity.clud.security;


import dev.identity.clud.exception.EmailAlreadyExistsException;
import dev.identity.clud.exception.InvalidTokenException;
import dev.identity.clud.jwt.AccessTokenResponse;
import dev.identity.clud.jwt.CookieService;
import dev.identity.clud.jwt.JwtProperties;
import dev.identity.clud.jwt.JwtService;
import dev.identity.clud.refreshSession.RefreshSession;
import dev.identity.clud.refreshSession.RefreshSessionRepository;
import dev.identity.clud.user.Role;
import dev.identity.clud.user.User;
import dev.identity.clud.user.UserRepository;
import dev.identity.clud.user.dto.LoginRequestDto;
import dev.identity.clud.user.dto.RegisterRequestDto;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
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

    @Transactional
    public void register(RegisterRequestDto request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("User already exists");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        try {
            userRepository.save(user);
            log.info("User registered: {}", normalizedEmail);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyExistsException("User already exists");
        }
    }

    @Transactional
    public AccessTokenResponse login(LoginRequestDto request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().trim().toLowerCase(),
                        request.getPassword()
                )
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        createRefreshSession(response, userDetails);

        log.info("User logged in: {}", userDetails.getId());
        return new AccessTokenResponse(jwtService.generateAccessToken(userDetails));
    }

    @Transactional
    public AccessTokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieService.getCookieValue(request, CookieService.REFRESH_TOKEN_COOKIE)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        UUID userId;
        try {
            userId = jwtService.extractUserId(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(userId.toString());

        if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
            throw new InvalidTokenException("User account is disabled or locked");
        }

        if (!jwtService.isTokenValid(refreshToken, userDetails, "refresh")) {
            throw new InvalidTokenException("Refresh token expired or invalid");
        }

        String tokenHash = hashToken(refreshToken);

        RefreshSession session = refreshSessionRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Session not found"));

        if (session.isRevoked()) {
            refreshSessionRepository.revokeAllByUserId(userId);
            log.warn("Token reuse detected for user: {}", userId);
            throw new InvalidTokenException("Token reuse detected. All sessions revoked.");
        }

        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Session expired");
        }

        session.setRevoked(true);
        refreshSessionRepository.save(session);

        createRefreshSession(response, userDetails);
        return new AccessTokenResponse(jwtService.generateAccessToken(userDetails));
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        cookieService.getCookieValue(request, CookieService.REFRESH_TOKEN_COOKIE).ifPresent(token -> {
            String tokenHash = hashToken(token);
            refreshSessionRepository.findByTokenHash(tokenHash).ifPresent(session -> {
                session.setRevoked(true);
                refreshSessionRepository.save(session);
            });
            cookieService.deleteCookie(response, CookieService.REFRESH_TOKEN_COOKIE);
        });
        log.info("User logged out");
    }

    private void createRefreshSession(HttpServletResponse response, CustomUserDetails userDetails) {
        String refreshToken = jwtService.generateRefreshToken(userDetails);
        String tokenHash = hashToken(refreshToken);
        String jti = jwtService.extractJti(refreshToken);

        RefreshSession session = RefreshSession.builder()
                .userId(userDetails.getId())
                .tokenHash(tokenHash)
                .jti(jti)
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiration()))
                .revoked(false)
                .build();

        refreshSessionRepository.save(session);

        cookieService.addTokenCookie(
                response,
                CookieService.REFRESH_TOKEN_COOKIE,
                refreshToken,
                jwtProperties.getRefreshTokenExpiration()
        );
    }

    private String hashToken(String token) {
        return DigestUtils.sha256Hex(token);
    }
}