package dev.storage.clud.storage;

import dev.storage.clud.exception.StorageException;
import io.minio.*;
import io.minio.messages.Item;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class StorageService {
    private final MinioClient minioClient;
    private final String bucketName = "mybucket";

    public StorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public String upload(MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();
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
            List<String> fileNames = new ArrayList<>();
            Iterable<Result<Item>> items = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).build()
            );
            for (Result<Item> item : items) {
                fileNames.add(item.get().objectName());
            }

            return fileNames;
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
            throw new StorageException("Failed to delete file: " + filename, e);
        }
    }
}
