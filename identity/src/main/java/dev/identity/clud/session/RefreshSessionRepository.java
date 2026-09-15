package dev.identity.clud.session;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshSession r WHERE r.tokenHash = :tokenHash")
    Optional<RefreshSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshSession r SET r.revoked = true WHERE r.userId = :userId AND r.revoked = false")
    void revokeAllByUserId(@Param("userId") UUID userId);
}
