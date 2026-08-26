package dev.identity.clud.security;


import dev.identity.clud.user.User;
import dev.identity.clud.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            log.warn("Authentication attempt with empty username");
            throw new UsernameNotFoundException("Username is empty");
        }

        String normalized = username.toLowerCase().trim();

        User user;
        if (isValidUuid(normalized)) {
            user = userRepository.findById(UUID.fromString(normalized))
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        } else {
            user = userRepository.findByEmail(normalized)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        }

        return CustomUserDetails.build(user);
    }

    private boolean isValidUuid(String str) {
        try {
            UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}