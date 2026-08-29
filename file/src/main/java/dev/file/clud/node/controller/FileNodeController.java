package dev.file.clud.node.controller;

import java.net.URI;
import java.util.UUID;

import dev.file.clud.node.mapper.NodeMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import dev.file.clud.node.dto.request.CreateFileRequest;
import dev.file.clud.node.dto.request.CreateFolderRequest;
import dev.file.clud.node.dto.request.RenameNodeRequest;
import dev.file.clud.node.dto.response.NodeResponse;
import dev.file.clud.node.dto.response.PageResponse;
import dev.file.clud.node.service.FileNodeService;

@Slf4j
@Validated
@RestController
@RequestMapping
@RequiredArgsConstructor
public class FileNodeController {

	private static final String USER_ID_HEADER = "X-User-ID";

	private final FileNodeService service;
	private final NodeMapper mapper;

	@PostMapping("/folders")
	ResponseEntity<NodeResponse> createFolder(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@Valid @RequestBody CreateFolderRequest request) {
		log.info("Creating folder for ownerId={}, name={}", ownerId, request.name());

		NodeResponse response = mapper.toResponse(service.createFolder(ownerId, request));
		return ResponseEntity.created(URI.create("/nodes/" + response.id())).body(response);
	}

	@PostMapping("/files")
	ResponseEntity<NodeResponse> createFile(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@Valid @RequestBody CreateFileRequest request) {
		log.info("Creating file for ownerId={}, name={}", ownerId, request.name());

		NodeResponse response = mapper.toResponse(service.createFile(ownerId, request));
		return ResponseEntity.created(URI.create("/nodes/" + response.id())).body(response);
	}

	@GetMapping("/nodes/{nodeId}")
	NodeResponse getNode(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@PathVariable UUID nodeId) {
		log.debug("Fetching node: nodeId={}, ownerId={}", nodeId, ownerId);

		return mapper.toResponse(service.get(ownerId, nodeId));
	}

	@GetMapping("/nodes")
	PageResponse<NodeResponse> listChildren(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@RequestParam(required = false) UUID parentId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
		log.debug("Listing children: ownerId={}, parentId={}, page={}, size={}", ownerId, parentId, page, size);

		return mapper.toPageResponse(service.listChildren(
				ownerId,
				parentId,
				PageRequest.of(page, size, Sort.by("type", "name"))));
	}

	@PatchMapping("/nodes/{nodeId}")
	NodeResponse renameNode(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@PathVariable UUID nodeId,
			@Valid @RequestBody RenameNodeRequest request) {
		log.info("Renaming node: nodeId={}, ownerId={}, newName={}", nodeId, ownerId, request.name());

		return mapper.toResponse(service.rename(ownerId, nodeId, request));
	}

	@PostMapping("/nodes/{nodeId}/move")
	NodeResponse moveNode(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@PathVariable UUID nodeId,
			@RequestParam UUID targetParentId) {
		log.info("Moving node: nodeId={}, ownerId={}, parentId={}", nodeId, ownerId, targetParentId);

		return mapper.toResponse(service.move(ownerId, nodeId, targetParentId));
	}

	@DeleteMapping("/nodes/{nodeId}")
	ResponseEntity<Void> moveToTrash(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@PathVariable UUID nodeId) {
		log.info("Moving to trash: nodeId={}, ownerId={}", nodeId, ownerId);

		service.moveToTrash(ownerId, nodeId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/trash")
	PageResponse<NodeResponse> listTrash(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
		log.debug("Listing trash: ownerId={}, page={}, size={}", ownerId, page, size);

		return mapper.toPageResponse(service.listTrash(ownerId, PageRequest.of(page, size)));
	}

	@PostMapping("/trash/{nodeId}/restore")
	NodeResponse restoreNode(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@PathVariable UUID nodeId) {
		log.info("Restoring node from trash: nodeId={}, ownerId={}", nodeId, ownerId);

		return mapper.toResponse(service.restore(ownerId, nodeId));
	}
}
