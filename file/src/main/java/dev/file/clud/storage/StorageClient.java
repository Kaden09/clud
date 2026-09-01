package dev.file.clud.storage;

import java.io.OutputStream;

import dev.file.clud.error.UpstreamStorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class StorageClient {

    private final RestClient storageRestClient;

    public StoredObjectResponse store(MultipartFile file) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());
        try {
            StoredObjectResponse response = storageRestClient.post()
                    .uri("/objects")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, upstream) -> {
                        throw new UpstreamStorageException(
                                "Storage Service rejected the object with status " + upstream.getStatusCode(),
                                null);
                    })
                    .body(StoredObjectResponse.class);
            if (response == null || response.storageKey() == null || response.storageKey().isBlank()) {
                throw new UpstreamStorageException("Storage Service returned an invalid response", null);
            }
            return response;
        }
        catch (UpstreamStorageException exception) {
            throw exception;
        }
        catch (RestClientException exception) {
            throw new UpstreamStorageException("Storage Service is unavailable", exception);
        }
    }

    public void copyTo(String storageKey, OutputStream outputStream) {
        try {
            storageRestClient.get()
                    .uri("/objects/{storageKey}", storageKey)
                    .exchange((request, upstream) -> {
                        if (upstream.getStatusCode().isError()) {
                            throw new UpstreamStorageException(
                                    "Storage Service could not read the object (status "
                                            + upstream.getStatusCode() + ")",
                                    null);
                        }
                        upstream.getBody().transferTo(outputStream);
                        return null;
                    });
        }
        catch (UpstreamStorageException exception) {
            throw exception;
        }
        catch (RestClientException exception) {
            throw new UpstreamStorageException("Storage Service is unavailable", exception);
        }
    }

    public void delete(String storageKey) {
        try {
            storageRestClient.delete()
                    .uri("/objects/{storageKey}", storageKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, upstream) -> {
                        throw new UpstreamStorageException(
                                "Storage Service could not delete the object (status "
                                        + upstream.getStatusCode() + ")",
                                null);
                    })
                    .toBodilessEntity();
        }
        catch (UpstreamStorageException exception) {
            throw exception;
        }
        catch (RestClientException exception) {
            throw new UpstreamStorageException("Storage Service is unavailable", exception);
        }
    }
}
