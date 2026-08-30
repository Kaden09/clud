package dev.sharing.clud.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import dev.sharing.clud.error.StorageIntegrationPendingException;

@Component
public class PendingStorageDownloadGateway implements StorageDownloadGateway {

	private final String storageServiceUrl;

	public PendingStorageDownloadGateway(
			@Value("${clud.sharing.storage-service-url}") String storageServiceUrl) {
		this.storageServiceUrl = storageServiceUrl;
	}

	@Override
	public ResponseEntity<Resource> download(PublicShareFile file) {
		/*
		 * TODO(storage): replace this placeholder when Storage Service exposes a stable
		 * internal download contract. The implementation should request the object from
		 * storageServiceUrl by its opaque storageKey, stream the response, and preserve
		 * Content-Type, Content-Length, and Content-Disposition. The storageKey must never
		 * be returned to the public caller.
		 */
		throw new StorageIntegrationPendingException();
	}

	String storageServiceUrl() {
		return storageServiceUrl;
	}
}
