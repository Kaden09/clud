package dev.storage.clud.storage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
@Tag(name = "Storage", description = "Storage operations")
public class StorageController {
    private final StorageService service;

    @PostMapping("/upload")
    @Operation(summary = "Upload file")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.upload(file));
    }

    @GetMapping("/files")
    @Operation(summary = "Find all files")
    public ResponseEntity<List<String>> findAllFiles() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/download/{filename}")
    @Operation(summary = "Download file")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(service.download(filename));
    }

    @DeleteMapping("/{filename}")
    @Operation(summary = "Delete file")
    public ResponseEntity<Void> deleteFile(@PathVariable String filename) {
        service.delete(filename);
        return ResponseEntity.ok().build();
    }
}
