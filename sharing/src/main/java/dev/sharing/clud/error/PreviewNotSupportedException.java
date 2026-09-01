package dev.sharing.clud.error;

public class PreviewNotSupportedException extends RuntimeException {
    public PreviewNotSupportedException(String contentType) {
        super("Preview is not supported for content type: " + contentType);
    }
}
