package dev.sharing.clud.share.controller;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
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

import dev.sharing.clud.share.dto.CreateShareLinkRequest;
import dev.sharing.clud.share.dto.CreatedShareLinkResponse;
import dev.sharing.clud.share.dto.ShareLinkResponse;
import dev.sharing.clud.share.service.ShareLinkService;
import dev.sharing.clud.storage.StorageDownloadGateway;

@Validated
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ShareLinkController {

	private static final String USER_ID_HEADER = "X-User-ID";

	private final ShareLinkService service;
	private final StorageDownloadGateway storageDownloadGateway;

	@PostMapping("/links")
	ResponseEntity<CreatedShareLinkResponse> create(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@Valid @RequestBody CreateShareLinkRequest request) {
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
		service.revoke(ownerId, linkId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/public/{token}")
	ResponseEntity<Resource> download(
			@PathVariable
			@Size(min = 32, max = 128, message = "Public token has an invalid length")
			String token) {
		return storageDownloadGateway.download(service.resolveForDownload(token));
	}
}
