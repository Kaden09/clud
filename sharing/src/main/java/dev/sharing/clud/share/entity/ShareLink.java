package dev.sharing.clud.share.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "share_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShareLink {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID ownerId;

	@Column(nullable = false, updatable = false)
	private UUID fileId;

	@Column(nullable = false, updatable = false, length = 64)
	private String tokenHash;

	private Instant expiresAt;

	private Instant revokedAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	private ShareLink(UUID ownerId, UUID fileId, String tokenHash, Instant expiresAt) {
		this.id = UUID.randomUUID();
		this.ownerId = ownerId;
		this.fileId = fileId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public static ShareLink create(UUID ownerId, UUID fileId, String tokenHash, Instant expiresAt) {
		return new ShareLink(ownerId, fileId, tokenHash, expiresAt);
	}

	public boolean isExpired(Instant now) {
		return expiresAt != null && !expiresAt.isAfter(now);
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public void revoke(Instant revokedAt) {
		if (this.revokedAt == null) {
			this.revokedAt = revokedAt;
		}
	}
}
