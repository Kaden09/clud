package dev.sharing.clud.share.service;

import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import dev.sharing.clud.client.FileNodeResponse;
import dev.sharing.clud.client.FileServiceClient;
import dev.sharing.clud.error.ShareLinkExpiredException;
import dev.sharing.clud.error.ShareLinkNotFoundException;
import dev.sharing.clud.event.FileSharedEvent;
import dev.sharing.clud.share.dto.CreateShareLinkRequest;
import dev.sharing.clud.share.dto.CreatedShareLinkResponse;
import dev.sharing.clud.share.dto.ShareLinkResponse;
import dev.sharing.clud.share.entity.ShareLink;
import dev.sharing.clud.share.repository.ShareLinkRepository;
import dev.sharing.clud.storage.PublicShareFile;

@Service
@RequiredArgsConstructor
public class ShareLinkService {

	private final ShareLinkRepository repository;
	private final ShareTokenService tokenService;
	private final FileServiceClient fileServiceClient;
	private final ApplicationEventPublisher eventPublisher;
	private final TransactionTemplate transactionTemplate;

	@Value("${clud.sharing.public-base-url}")
	private String publicBaseUrl;

	public CreatedShareLinkResponse create(UUID ownerId, CreateShareLinkRequest request) {
		fileServiceClient.getActiveFile(ownerId, request.fileId());
		ShareTokenService.GeneratedToken token = tokenService.generate();

		return transactionTemplate.execute(status -> {
			Instant now = Instant.now();
			repository.findByOwnerIdAndFileIdAndRevokedAtIsNull(ownerId, request.fileId())
					.ifPresent(existing -> existing.revoke(now));

			ShareLink link = repository.saveAndFlush(ShareLink.create(
					ownerId,
					request.fileId(),
					token.hash(),
					request.expiresAt()));
			eventPublisher.publishEvent(FileSharedEvent.from(link));

			return new CreatedShareLinkResponse(
					link.getId(),
					link.getFileId(),
					publicUrl(token.value()),
					link.getExpiresAt(),
					link.getCreatedAt());
		});
	}

	public ShareLinkResponse getActive(UUID ownerId, UUID fileId) {
		ShareLink link = repository.findByOwnerIdAndFileIdAndRevokedAtIsNull(ownerId, fileId)
				.filter(candidate -> !candidate.isExpired(Instant.now()))
				.orElseThrow(ShareLinkNotFoundException::new);

		return new ShareLinkResponse(link.getId(), link.getFileId(), link.getExpiresAt(), link.getCreatedAt());
	}

	@Transactional
	public void revoke(UUID ownerId, UUID linkId) {
		ShareLink link = repository.findByIdAndOwnerId(linkId, ownerId)
				.orElseThrow(ShareLinkNotFoundException::new);
		link.revoke(Instant.now());
	}

	public PublicShareFile resolveForDownload(String rawToken) {
		ShareLink link = repository.findByTokenHashAndRevokedAtIsNull(tokenService.hash(rawToken))
				.orElseThrow(ShareLinkNotFoundException::new);
		if (link.isExpired(Instant.now())) {
			throw new ShareLinkExpiredException();
		}

		FileNodeResponse file = fileServiceClient.getActiveFile(link.getOwnerId(), link.getFileId());
		return new PublicShareFile(
				file.id(),
				file.storageKey(),
				file.name(),
				file.contentType(),
				file.sizeBytes());
	}

	@Transactional
	public int revokeAllForFile(UUID ownerId, UUID fileId) {
		return repository.revokeAllActiveByOwnerIdAndFileId(ownerId, fileId, Instant.now());
	}

	private String publicUrl(String token) {
		return publicBaseUrl.replaceAll("/+$", "") + "/" + token;
	}
}
