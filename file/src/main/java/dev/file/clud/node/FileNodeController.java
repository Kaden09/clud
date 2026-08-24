package dev.file.clud.node;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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

@RestController
@RequestMapping
@Validated
public class FileNodeController {

	private static final String USER_ID_HEADER = "X-User-ID";

	private final FileNodeService service;

	public FileNodeController(FileNodeService service) {
		this.service = service;
	}

	@PostMapping("/folders")
	ResponseEntity<NodeResponse> createFolder(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@Valid @RequestBody CreateFolderRequest request) {
		NodeResponse response = NodeResponse.from(service.createFolder(ownerId, request));
		return ResponseEntity.created(URI.create("/nodes/" + response.id())).body(response);
	}

	@PostMapping("/files")
	ResponseEntity<NodeResponse> createFile(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@Valid @RequestBody CreateFileRequest request) {
		NodeResponse response = NodeResponse.from(service.createFile(ownerId, request));
		return ResponseEntity.created(URI.create("/nodes/" + response.id())).body(response);
	}

	@GetMapping("/nodes/{nodeId}")
	NodeResponse getNode(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@PathVariable UUID nodeId) {
		return NodeResponse.from(service.get(ownerId, nodeId));
	}

	@GetMapping("/nodes")
	PageResponse<NodeResponse> listChildren(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@RequestParam(required = false) UUID parentId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
		return PageResponse.fromNodes(service.listChildren(
				ownerId,
				parentId,
				PageRequest.of(page, size, Sort.by("type", "name"))));
	}

	@PatchMapping("/nodes/{nodeId}")
	NodeResponse renameNode(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@PathVariable UUID nodeId,
			@Valid @RequestBody RenameNodeRequest request) {
		return NodeResponse.from(service.rename(ownerId, nodeId, request));
	}

	@PostMapping("/nodes/{nodeId}/move")
	NodeResponse moveNode(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@PathVariable UUID nodeId,
			@RequestBody MoveNodeRequest request) {
		return NodeResponse.from(service.move(ownerId, nodeId, request));
	}

	@DeleteMapping("/nodes/{nodeId}")
	ResponseEntity<Void> moveToTrash(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@PathVariable UUID nodeId) {
		service.moveToTrash(ownerId, nodeId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/trash")
	PageResponse<NodeResponse> listTrash(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
		return PageResponse.fromNodes(service.listTrash(ownerId, PageRequest.of(page, size)));
	}

	@PostMapping("/trash/{nodeId}/restore")
	NodeResponse restoreNode(
			@RequestHeader(USER_ID_HEADER) UUID ownerId,
			@PathVariable UUID nodeId) {
		return NodeResponse.from(service.restore(ownerId, nodeId));
	}
}
