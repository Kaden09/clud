package dev.sharing.clud.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PreviewPolicyTests {

    private final PreviewPolicy policy = new PreviewPolicy();

    @ParameterizedTest
    @ValueSource(strings = {
            "application/pdf",
            "application/json; charset=UTF-8",
            "text/plain",
            "text/csv",
            "image/png",
            "image/jpeg",
            "audio/mpeg",
            "video/mp4"
    })
    void allowsPassivePreviewTypes(String contentType) {
        assertThat(policy.supports(contentType)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "text/html",
            "application/xhtml+xml",
            "image/svg+xml",
            "application/xml",
            "application/javascript",
            "application/zip"
    })
    void blocksActiveAndUnsupportedPreviewTypes(String contentType) {
        assertThat(policy.supports(contentType)).isFalse();
    }
}
