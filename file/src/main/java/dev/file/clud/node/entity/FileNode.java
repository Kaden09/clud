package dev.file.clud.node.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "file_nodes", schema = "file_service")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileNode {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", updatable = false, nullable = false)
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

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	public static FileNode folder(UUID ownerId, FileNode parent, String name) {
		return FileNode.builder()
				.ownerId(ownerId)
				.parent(parent)
				.type(NodeType.FOLDER)
				.name(name)
				.build();
	}

	public static FileNode file(
			UUID ownerId,
			FileNode parent,
			String name,
			String storageKey,
			String contentType,
			long sizeBytes) {
		return FileNode.builder()
				.ownerId(ownerId)
				.parent(parent)
				.type(NodeType.FILE)
				.name(name)
				.storageKey(storageKey.trim())
				.contentType(contentType.trim())
				.sizeBytes(sizeBytes)
				.build();
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
}
