package dev.identity.clud.session;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshSessionRevocationService {

    private final RefreshSessionRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAll(UUID userId) {
        repository.revokeAllByUserId(userId);
    }
}
