package dev.file.clud.content;

import java.util.UUID;

import dev.file.clud.error.InvalidNodeOperationException;
import dev.file.clud.node.entity.FileNode;
import dev.file.clud.node.service.FileNodeService;
import dev.file.clud.storage.StorageClient;
import dev.file.clud.storage.StoredObjectResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileContentService {

    private final FileNodeService fileNodeService;
    private final StorageClient storageClient;
    private final TransactionTemplate transactionTemplate;

    public FileContentService(
            FileNodeService fileNodeService,
            StorageClient storageClient,
            PlatformTransactionManager transactionManager) {
        this.fileNodeService = fileNodeService;
        this.storageClient = storageClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public FileNode upload(UUID ownerId, UUID parentId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidNodeOperationException("File must not be empty");
        }
        String name = safeFileName(file.getOriginalFilename());
        transactionTemplate.executeWithoutResult(status ->
                fileNodeService.validateUploadTarget(ownerId, parentId, name));

        StoredObjectResponse stored = storageClient.store(file);
        try {
            return transactionTemplate.execute(status -> fileNodeService.createStoredFile(
                    ownerId,
                    parentId,
                    name,
                    stored.storageKey(),
                    stored.contentType(),
                    stored.sizeBytes()));
        }
        catch (RuntimeException metadataFailure) {
            try {
                storageClient.delete(stored.storageKey());
            }
            catch (RuntimeException cleanupFailure) {
                metadataFailure.addSuppressed(cleanupFailure);
            }
            throw metadataFailure;
        }
    }

    public FileDownload getDownload(UUID ownerId, UUID fileId) {
        FileNode file = transactionTemplate.execute(status -> fileNodeService.getActiveFile(ownerId, fileId));
        if (file == null) {
            throw new IllegalStateException("File lookup returned no result");
        }
        return new FileDownload(file.getName(), file.getStorageKey(), file.getContentType(), file.getSizeBytes());
    }

    public void copyTo(String storageKey, java.io.OutputStream outputStream) {
        storageClient.copyTo(storageKey, outputStream);
    }

    private String safeFileName(String originalFilename) {
        if (originalFilename == null) {
            throw new InvalidNodeOperationException("File name is required");
        }
        String normalized = originalFilename.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            throw new InvalidNodeOperationException("File name is invalid");
        }
        if (name.length() > 255) {
            throw new InvalidNodeOperationException("File name must not exceed 255 characters");
        }
        return name;
    }
}
