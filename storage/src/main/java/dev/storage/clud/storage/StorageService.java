package dev.storage.clud.storage;

import java.util.UUID;

import dev.storage.clud.exception.InvalidStorageObjectException;
import dev.storage.clud.exception.NotFoundException;
import dev.storage.clud.exception.StorageException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket.name:clud-files}")
    private String bucketName;

    public StoredObject store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidStorageObjectException("File must not be empty");
        }

        String storageKey = UUID.randomUUID().toString();
        String contentType = file.getContentType() == null || file.getContentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : file.getContentType();
        try {
            ensureBucketExists();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storageKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType)
                    .build());
            return new StoredObject(storageKey, contentType, file.getSize());
        }
        catch (Exception exception) {
            throw new StorageException("Failed to store object", exception);
        }
    }

    public StorageObjectResource download(String storageKey) {
        validateStorageKey(storageKey);
        try {
            StatObjectResponse metadata = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storageKey)
                    .build());
            String contentType = metadata.contentType() == null || metadata.contentType().isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                    : metadata.contentType();
            return new StorageObjectResource(
                    new InputStreamResource(minioClient.getObject(GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storageKey)
                            .build())),
                    MediaType.parseMediaType(contentType),
                    metadata.size());
        }
        catch (ErrorResponseException exception) {
            throw mapMinioError(storageKey, exception);
        }
        catch (Exception exception) {
            throw new StorageException("Failed to download object: " + storageKey, exception);
        }
    }

    public void delete(String storageKey) {
        validateStorageKey(storageKey);
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storageKey)
                    .build());
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storageKey)
                    .build());
        }
        catch (ErrorResponseException exception) {
            throw mapMinioError(storageKey, exception);
        }
        catch (Exception exception) {
            throw new StorageException("Failed to delete object: " + storageKey, exception);
        }
    }

    private synchronized void ensureBucketExists() throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    private void validateStorageKey(String storageKey) {
        try {
            if (!UUID.fromString(storageKey).toString().equals(storageKey)) {
                throw new IllegalArgumentException();
            }
        }
        catch (IllegalArgumentException exception) {
            throw new InvalidStorageObjectException("Storage key must be a canonical UUID");
        }
    }

    private RuntimeException mapMinioError(String storageKey, ErrorResponseException exception) {
        String code = exception.errorResponse().code();
        if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NoSuchBucket".equals(code)) {
            return new NotFoundException("Object not found: " + storageKey);
        }
        return new StorageException("Storage operation failed for object: " + storageKey, exception);
    }
}
