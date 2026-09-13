package dev.file.clud.node.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.file.clud.node.entity.FileNode;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileNodeRepository extends JpaRepository<FileNode, UUID> {

    @Query("SELECT COALESCE(SUM(n.sizeBytes), 0L) FROM FileNode n WHERE n.ownerId = :ownerId AND n.sizeBytes IS NOT NULL")
    long sumSizeBytesForOwner(@Param("ownerId") UUID ownerId);

    Optional<FileNode> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("""
            SELECT node
            FROM FileNode node
            WHERE node.ownerId = :ownerId
              AND node.parent.id = :parentId
              AND node.deletedAt IS NULL
            ORDER BY node.type, node.name
            """)
    Page<FileNode> findActiveChildren(
            @Param("ownerId") UUID ownerId,
            @Param("parentId") UUID parentId,
            Pageable pageable);

    @Query("""
            SELECT node
            FROM FileNode node
            WHERE node.ownerId = :ownerId
              AND node.parent IS NULL
              AND node.deletedAt IS NULL
            ORDER BY node.type, node.name
            """)
    Page<FileNode> findActiveRootChildren(
            @Param("ownerId") UUID ownerId,
            Pageable pageable);

    Page<FileNode> findByOwnerIdAndTrashRootTrueOrderByDeletedAtDesc(UUID ownerId, Pageable pageable);

    List<FileNode> findByOwnerIdAndParentId(UUID ownerId, UUID parentId);

    List<FileNode> findByOwnerIdAndTrashRootTrue(UUID ownerId);

    boolean existsByOwnerIdAndParentIdAndNameIgnoreCaseAndDeletedAtIsNull(
            UUID ownerId,
            UUID parentId,
            String name);

    boolean existsByOwnerIdAndParentIsNullAndNameIgnoreCaseAndDeletedAtIsNull(UUID ownerId, String name);
}
