package dev.identity.clud.refreshSession;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "refresh_sessions", indexes = {
        @Index(name = "idx_refresh_sessions_user_id", columnList = "userId"),
        @Index(name = "idx_refresh_sessions_expires_at", columnList = "expiresAt"),
        @Index(name = "idx_refresh_sessions_revoked", columnList = "revoked")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 36)
    private String jti;

    @Column(nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;
}