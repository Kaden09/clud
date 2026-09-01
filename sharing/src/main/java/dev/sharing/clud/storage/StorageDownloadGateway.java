package dev.sharing.clud.storage;

import java.io.OutputStream;

import dev.sharing.clud.error.SharedFileNotFoundException;
import dev.sharing.clud.error.UpstreamServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class StorageDownloadGateway {

    private final RestClient storageServiceRestClient;

    public StorageDownloadGateway(
            @Qualifier("storageServiceRestClient") RestClient storageServiceRestClient) {
        this.storageServiceRestClient = storageServiceRestClient;
    }

    public void assertReadable(PublicShareFile file) {
        try {
            storageServiceRestClient.head()
                    .uri("/objects/{storageKey}", file.storageKey())
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, upstream) -> {
                        throw new SharedFileNotFoundException(file.fileId());
                    })
                    .onStatus(HttpStatusCode::isError, (request, upstream) -> {
                        throw new UpstreamServiceException(
                                "Storage Service could not read the shared object (status "
                                        + upstream.getStatusCode() + ")",
                                null);
                    })
                    .toBodilessEntity();
        }
        catch (SharedFileNotFoundException | UpstreamServiceException exception) {
            throw exception;
        }
        catch (RestClientException exception) {
            throw new UpstreamServiceException("Storage Service is unavailable", exception);
        }
    }

    public void copyTo(PublicShareFile file, OutputStream outputStream) {
        try {
            storageServiceRestClient.get()
                    .uri("/objects/{storageKey}", file.storageKey())
                    .exchange((request, upstream) -> {
                        HttpStatusCode status = upstream.getStatusCode();
                        if (status.value() == 404) {
                            throw new SharedFileNotFoundException(file.fileId());
                        }
                        if (status.isError()) {
                            throw new UpstreamServiceException(
                                    "Storage Service could not read the shared object (status " + status + ")",
                                    null);
                        }
                        upstream.getBody().transferTo(outputStream);
                        return null;
                    });
        }
        catch (SharedFileNotFoundException | UpstreamServiceException exception) {
            throw exception;
        }
        catch (RestClientException exception) {
            throw new UpstreamServiceException("Storage Service is unavailable", exception);
        }
    }
}
