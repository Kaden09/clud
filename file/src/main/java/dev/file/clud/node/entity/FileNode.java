package dev.file.clud.node.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "file_nodes", schema = "file_service")
public class FileNode {

	@Id
	private UUID id;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private UUID ownerId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private FileNode parent;

	@Enumerated(EnumType.STRING)
	@Column(name = "node_type", nullable = false, updatable = false)
	private NodeType type;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(name = "storage_key", length = 512)
	private String storageKey;

	@Column(name = "content_type", length = 255)
	private String contentType;

	@Column(name = "size_bytes")
	private Long sizeBytes;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "trash_root", nullable = false)
	private boolean trashRoot;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected FileNode() {
	}

	private FileNode(UUID ownerId, FileNode parent, NodeType type, String name) {
		this.id = UUID.randomUUID();
		this.ownerId = ownerId;
		this.parent = parent;
		this.type = type;
		this.name = name.trim();
	}

	public static FileNode folder(UUID ownerId, FileNode parent, String name) {
		return new FileNode(ownerId, parent, NodeType.FOLDER, name);
	}

	public static FileNode file(
			UUID ownerId,
			FileNode parent,
			String name,
			String storageKey,
			String contentType,
			long sizeBytes) {
		FileNode node = new FileNode(ownerId, parent, NodeType.FILE, name);
		node.storageKey = storageKey.trim();
		node.contentType = contentType.trim();
		node.sizeBytes = sizeBytes;
		return node;
	}

	public void rename(String name) {
		this.name = name.trim();
	}

	public void moveTo(FileNode parent) {
		this.parent = parent;
	}

	public void moveToTrash(Instant deletedAt, boolean root) {
		this.deletedAt = deletedAt;
		this.trashRoot = root;
	}

	public void restore() {
		this.deletedAt = null;
		this.trashRoot = false;
	}

	public boolean isFolder() {
		return type == NodeType.FOLDER;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public FileNode getParent() {
		return parent;
	}

	public NodeType getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public String getContentType() {
		return contentType;
	}

	public Long getSizeBytes() {
		return sizeBytes;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public boolean isTrashRoot() {
		return trashRoot;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public long getVersion() {
		return version;
	}
}
