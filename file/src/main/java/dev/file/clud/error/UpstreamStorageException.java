package dev.file.clud.error;

public class UpstreamStorageException extends RuntimeException {
    public UpstreamStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
