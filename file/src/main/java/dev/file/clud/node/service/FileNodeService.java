package dev.file.clud.node.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.file.clud.error.InvalidNodeOperationException;
import dev.file.clud.error.NodeConflictException;
import dev.file.clud.error.NodeNotFoundException;
import dev.file.clud.event.FileLifecycleEvent;
import dev.file.clud.node.dto.request.CreateFolderRequest;
import dev.file.clud.node.dto.request.RenameNodeRequest;
import dev.file.clud.node.entity.FileNode;
import dev.file.clud.node.repository.FileNodeRepository;

@Service
@RequiredArgsConstructor
public class FileNodeService {

	private final FileNodeRepository repository;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public FileNode createFolder(UUID ownerId, CreateFolderRequest request) {
		FileNode parent = resolveActiveFolder(ownerId, request.parentId());
		ensureNameAvailable(ownerId, request.parentId(), request.name());
		return repository.save(FileNode.folder(ownerId, parent, request.name()));
	}

	@Transactional(readOnly = true)
	public void validateUploadTarget(UUID ownerId, UUID parentId, String name) {
		resolveActiveFolder(ownerId, parentId);
		ensureNameAvailable(ownerId, parentId, name);
	}

	@Transactional
	public FileNode createStoredFile(
			UUID ownerId,
			UUID parentId,
			String name,
			String storageKey,
			String contentType,
			long sizeBytes) {
		FileNode parent = resolveActiveFolder(ownerId, parentId);
		ensureNameAvailable(ownerId, parentId, name);
		FileNode file = repository.saveAndFlush(FileNode.file(
				ownerId,
				parent,
				name,
				storageKey,
				contentType,
				sizeBytes));
		eventPublisher.publishEvent(FileLifecycleEvent.uploaded(file));
		return file;
	}

	@Transactional(readOnly = true)
	public FileNode get(UUID ownerId, UUID nodeId) {
		return getActive(ownerId, nodeId);
	}

	@Transactional(readOnly = true)
	public FileNode getActiveFile(UUID ownerId, UUID nodeId) {
		FileNode node = getActive(ownerId, nodeId);
		if (node.isFolder()) {
			throw new InvalidNodeOperationException("Folder content cannot be downloaded");
		}
		return node;
	}

	@Transactional(readOnly = true)
	public Page<FileNode> listChildren(UUID ownerId, UUID parentId, Pageable pageable) {
		if (parentId == null) {
			return repository.findByOwnerIdAndParentIsNullAndDeletedAtIsNull(ownerId, pageable);
		}
		FileNode parent = getActive(ownerId, parentId);
		ensureFolder(parent);
		return repository.findByOwnerIdAndParentIdAndDeletedAtIsNull(ownerId, parentId, pageable);
	}

	@Transactional(readOnly = true)
	public Page<FileNode> listTrash(UUID ownerId, Pageable pageable) {
		return repository.findByOwnerIdAndTrashRootTrueOrderByDeletedAtDesc(ownerId, pageable);
	}

	@Transactional
	public FileNode rename(UUID ownerId, UUID nodeId, RenameNodeRequest request) {
		FileNode node = getActive(ownerId, nodeId);
		if (node.getName().equalsIgnoreCase(request.name().trim())) {
			node.rename(request.name());
			return node;
		}
		UUID parentId = node.getParent() == null ? null : node.getParent().getId();
		ensureNameAvailable(ownerId, parentId, request.name());
		node.rename(request.name());
		return node;
	}

	@Transactional
	public FileNode move(UUID ownerId, UUID nodeId, UUID targetParentId) {
		FileNode node = getActive(ownerId, nodeId);
		FileNode destination = resolveActiveFolder(ownerId, targetParentId);
		UUID currentParentId = node.getParent() == null ? null : node.getParent().getId();
		if (Objects.equals(currentParentId, targetParentId)) {
			return node;
		}
		if (node.isFolder()) {
			ensureNotDescendant(node, destination);
		}
		ensureNameAvailable(ownerId, targetParentId, node.getName());
		node.moveTo(destination);
		return node;
	}

	@Transactional
	public void moveToTrash(UUID ownerId, UUID nodeId) {
		FileNode root = getActive(ownerId, nodeId);
		List<FileNode> tree = collectTree(ownerId, root);
		Instant deletedAt = Instant.now();
		for (FileNode node : tree) {
			node.moveToTrash(deletedAt, node == root);
			if (!node.isFolder()) {
				eventPublisher.publishEvent(FileLifecycleEvent.deleted(node));
			}
		}
	}

	@Transactional
	public FileNode restore(UUID ownerId, UUID nodeId) {
		FileNode root = repository.findByIdAndOwnerId(nodeId, ownerId)
				.orElseThrow(() -> new NodeNotFoundException(nodeId));
		if (!root.isDeleted() || !root.isTrashRoot()) {
			throw new InvalidNodeOperationException("Only a top-level trash item can be restored");
		}
		FileNode parent = root.getParent();
		if (parent != null && parent.isDeleted()) {
			throw new NodeConflictException("Restore the parent folder before restoring this node");
		}
		UUID parentId = parent == null ? null : parent.getId();
		ensureNameAvailable(ownerId, parentId, root.getName());
		for (FileNode node : collectTree(ownerId, root)) {
			node.restore();
		}
		return root;
	}

	private FileNode getActive(UUID ownerId, UUID nodeId) {
		FileNode node = repository.findByIdAndOwnerId(nodeId, ownerId)
				.orElseThrow(() -> new NodeNotFoundException(nodeId));
		if (node.isDeleted()) {
			throw new NodeNotFoundException(nodeId);
		}
		return node;
	}

	private FileNode resolveActiveFolder(UUID ownerId, UUID parentId) {
		if (parentId == null) {
			return null;
		}
		FileNode parent = getActive(ownerId, parentId);
		ensureFolder(parent);
		return parent;
	}

	private void ensureFolder(FileNode node) {
		if (!node.isFolder()) {
			throw new InvalidNodeOperationException("A file cannot contain child nodes");
		}
	}

	private void ensureNotDescendant(FileNode node, FileNode destination) {
		FileNode current = destination;
		while (current != null) {
			if (current.getId().equals(node.getId())) {
				throw new InvalidNodeOperationException("A folder cannot be moved into itself or its descendant");
			}
			current = current.getParent();
		}
	}

	private void ensureNameAvailable(UUID ownerId, UUID parentId, String name) {
		boolean exists = parentId == null
				? repository.existsByOwnerIdAndParentIsNullAndNameIgnoreCaseAndDeletedAtIsNull(ownerId, name.trim())
				: repository.existsByOwnerIdAndParentIdAndNameIgnoreCaseAndDeletedAtIsNull(
						ownerId,
						parentId,
						name.trim());
		if (exists) {
			throw new NodeConflictException("A node with this name already exists in the destination folder");
		}
	}

	private List<FileNode> collectTree(UUID ownerId, FileNode root) {
		List<FileNode> nodes = new ArrayList<>();
		collectTree(ownerId, root, nodes);
		return nodes;
	}

	private void collectTree(UUID ownerId, FileNode node, List<FileNode> nodes) {
		nodes.add(node);
		for (FileNode child : repository.findByOwnerIdAndParentId(ownerId, node.getId())) {
			collectTree(ownerId, child, nodes);
		}
	}
}
