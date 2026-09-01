package dev.storage.clud.storage;

import java.net.URI;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/objects")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService service;

    @PostMapping
    ResponseEntity<StoredObjectResponse> store(@RequestParam("file") MultipartFile file) {
        StoredObject stored = service.store(file);
        return ResponseEntity.created(URI.create("/objects/" + stored.storageKey()))
                .body(new StoredObjectResponse(stored.storageKey(), stored.contentType(), stored.sizeBytes()));
    }

    @GetMapping("/{storageKey}")
    ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable String storageKey) {
        StorageObjectResource object = service.download(storageKey);
        return ResponseEntity.ok()
                .contentType(object.contentType())
                .contentLength(object.sizeBytes())
                .body(object.resource());
    }

    @RequestMapping(value = "/{storageKey}", method = RequestMethod.HEAD)
    ResponseEntity<Void> metadata(@PathVariable String storageKey) {
        StoredObjectMetadata object = service.metadata(storageKey);
        return ResponseEntity.ok()
                .contentType(object.contentType())
                .contentLength(object.sizeBytes())
                .build();
    }

    @DeleteMapping("/{storageKey}")
    ResponseEntity<Void> delete(@PathVariable String storageKey) {
        service.delete(storageKey);
        return ResponseEntity.noContent().build();
    }
}
