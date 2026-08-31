package dev.sharing.clud.share.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.sharing.clud.share.entity.ShareLink;

public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {

	Optional<ShareLink> findByOwnerIdAndFileIdAndRevokedAtIsNull(UUID ownerId, UUID fileId);

	Optional<ShareLink> findByIdAndOwnerId(UUID id, UUID ownerId);

	Optional<ShareLink> findByTokenHashAndRevokedAtIsNull(String tokenHash);

	@Modifying
	@Query("""
			update ShareLink link
			set link.revokedAt = :revokedAt,
				link.updatedAt = :revokedAt,
				link.version = link.version + 1
			where link.ownerId = :ownerId
				and link.fileId = :fileId
				and link.revokedAt is null
			""")
	int revokeAllActiveByOwnerIdAndFileId(
			@Param("ownerId") UUID ownerId,
			@Param("fileId") UUID fileId,
			@Param("revokedAt") Instant revokedAt);
}
