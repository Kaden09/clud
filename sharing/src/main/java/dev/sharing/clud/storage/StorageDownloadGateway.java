package dev.sharing.clud.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

public interface StorageDownloadGateway {

	ResponseEntity<Resource> download(PublicShareFile file);
}
