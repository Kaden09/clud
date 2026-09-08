package dev.sharing.clud.share.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import dev.sharing.clud.share.dto.CreateShareLinkRequest;
import dev.sharing.clud.share.dto.CreatedShareLinkResponse;
import dev.sharing.clud.share.dto.ShareLinkResponse;
import dev.sharing.clud.share.dto.PublicFileMetadataResponse;
import dev.sharing.clud.share.service.ShareLinkService;
import dev.sharing.clud.error.PreviewNotSupportedException;
import dev.sharing.clud.storage.PreviewPolicy;
import dev.sharing.clud.storage.PublicShareFile;
import dev.sharing.clud.storage.StorageDownloadGateway;

@Slf4j
@Validated
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ShareLinkController {

	private static final String USER_ID_HEADER = "X-User-ID";

	private final ShareLinkService service;
	private final StorageDownloadGateway storageDownloadGateway;
	private final PreviewPolicy previewPolicy;

	@PostMapping("/links")
	ResponseEntity<CreatedShareLinkResponse> create(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@Valid @RequestBody CreateShareLinkRequest request) {
		log.info("Creating share link: ownerId={}, fileId={}", ownerId, request.fileId());
		CreatedShareLinkResponse response = service.create(ownerId, request);
		return ResponseEntity.created(URI.create("/links/" + response.id())).body(response);
	}

	@GetMapping("/links")
	ShareLinkResponse getActive(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@RequestParam UUID fileId) {
		return service.getActive(ownerId, fileId);
	}

	@DeleteMapping("/links/{linkId}")
	ResponseEntity<Void> revoke(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@PathVariable UUID linkId) {
		log.info("Revoking share link: ownerId={}, linkId={}", ownerId, linkId);
		service.revoke(ownerId, linkId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/public/{token}")
	ResponseEntity<PublicFileMetadataResponse> metadata(
			@PathVariable
			@Size(min = 32, max = 128, message = "Public token has an invalid length")
			String token) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.header("X-Content-Type-Options", "nosniff")
				.header("Referrer-Policy", "no-referrer")
				.body(service.getPublicMetadata(token));
	}

	@GetMapping("/public/{token}/preview")
	ResponseEntity<StreamingResponseBody> preview(
			@PathVariable
			@Size(min = 32, max = 128, message = "Public token has an invalid length")
			String token) {
		PublicShareFile file = service.resolveForAccess(token);
		if (!previewPolicy.supports(file.contentType())) {
			throw new PreviewNotSupportedException(file.contentType());
		}
		return stream(file, true);
	}

	@GetMapping("/public/{token}/download")
	ResponseEntity<StreamingResponseBody> download(
			@PathVariable
			@Size(min = 32, max = 128, message = "Public token has an invalid length")
			String token) {
		return stream(service.resolveForAccess(token), false);
	}

	private ResponseEntity<StreamingResponseBody> stream(PublicShareFile file, boolean inline) {
		storageDownloadGateway.assertReadable(file);
		ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
				.filename(file.name(), StandardCharsets.UTF_8)
				.build();
		StreamingResponseBody body = output -> storageDownloadGateway.copyTo(file, output);
		ResponseEntity.BodyBuilder response = ResponseEntity.ok()
				.contentType(mediaType(file.contentType()))
				.contentLength(file.sizeBytes())
				.cacheControl(CacheControl.noStore())
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.header("X-Content-Type-Options", "nosniff")
				.header("Referrer-Policy", "no-referrer");
		if (inline) {
			response.header("Content-Security-Policy", "sandbox; default-src 'none'");
		}
		return response.body(body);
	}

	private MediaType mediaType(String contentType) {
		if (contentType == null || contentType.isBlank()) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
		try {
			return MediaType.parseMediaType(contentType);
		}
		catch (InvalidMediaTypeException exception) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
	}
}
