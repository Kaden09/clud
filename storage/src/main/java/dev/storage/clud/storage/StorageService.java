package dev.storage.clud.storage;

import dev.storage.clud.exception.NotFoundException;
import dev.storage.clud.exception.StorageException;
import io.minio.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StorageService {
    private final MinioClient minioClient;

    @Value("${minio.bucket.name:clud-files}")
    private String bucketName;

    public String upload(MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();

            if(!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
            }

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            return fileName;
        } catch (Exception e) {
            throw new StorageException("Failed to upload file", e);
        }
    }

    public List<String> findAll() {
        try {
            if(!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                throw new NotFoundException("Bucket not found");
            }

            List<String> fileNames = new ArrayList<>();

            Iterable<Result<Item>> items = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).build()
            );
            for (Result<Item> item : items) {
                fileNames.add(item.get().objectName());
            }

            return fileNames;
        } catch (RuntimeException e) {
            if (e instanceof NotFoundException || e instanceof ResponseStatusException) {
                throw e;
            }
            throw new StorageException("Failed to list files", e);

        } catch (Exception e) {
            throw new StorageException("Failed to list files", e);
        }
    }

    public Resource download(String filename) {
        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .build()
            );
            return new InputStreamResource(stream);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("NoSuchKey")) {
                throw new NotFoundException("File not found: " + filename);
            }
            throw new StorageException("Failed to download file: " + filename, e);
        }
    }

    public void delete(String filename) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .build()
            );
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("NoSuchKey")) {
                throw new NotFoundException("File not found: " + filename);
            }
            throw new StorageException("Failed to download file: " + filename, e);
        }
    }
}
