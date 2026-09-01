package dev.sharing.clud.storage;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class PreviewPolicy {

    private static final Set<String> SAFE_EXACT_TYPES = Set.of(
            "application/pdf",
            "application/json",
            "text/plain",
            "text/csv");

    private static final Set<String> SAFE_IMAGE_TYPES = Set.of(
            "image/avif",
            "image/bmp",
            "image/gif",
            "image/jpeg",
            "image/png",
            "image/webp");

    public boolean supports(String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return SAFE_EXACT_TYPES.contains(normalized)
                || SAFE_IMAGE_TYPES.contains(normalized)
                || normalized.startsWith("audio/")
                || normalized.startsWith("video/");
    }
}
