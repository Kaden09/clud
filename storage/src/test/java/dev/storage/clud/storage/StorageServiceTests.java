package dev.storage.clud.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import dev.storage.clud.exception.InvalidStorageObjectException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StorageServiceTests {

    @Mock
    private MinioClient minioClient;

    private StorageService service;

    @BeforeEach
    void setUp() {
        service = new StorageService(minioClient);
        ReflectionTestUtils.setField(service, "bucketName", "test-files");
    }

    @Test
    void generatesOpaqueKeysSoEqualFileNamesDoNotCollide() throws Exception {
        when(minioClient.bucketExists(any())).thenReturn(true);
        MockMultipartFile first = file("report.pdf", "first");
        MockMultipartFile second = file("report.pdf", "second");

        StoredObject firstStored = service.store(first);
        StoredObject secondStored = service.store(second);

        assertThat(firstStored.storageKey()).isNotEqualTo(secondStored.storageKey());
        assertThat(UUID.fromString(firstStored.storageKey())).isNotNull();
        assertThat(UUID.fromString(secondStored.storageKey())).isNotNull();
        assertThat(firstStored.storageKey()).doesNotContain("report.pdf");

        ArgumentCaptor<PutObjectArgs> arguments = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient, times(2)).putObject(arguments.capture());
        assertThat(arguments.getAllValues())
                .extracting(PutObjectArgs::object)
                .containsExactly(firstStored.storageKey(), secondStored.storageKey());
    }

    @Test
    void rejectsEmptyObjectsBeforeCallingMinio() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.store(empty))
                .isInstanceOf(InvalidStorageObjectException.class)
                .hasMessage("File must not be empty");
    }

    @Test
    void rejectsNonUuidStorageKeys() {
        assertThatThrownBy(() -> service.download("report.pdf"))
                .isInstanceOf(InvalidStorageObjectException.class)
                .hasMessage("Storage key must be a canonical UUID");
    }

    @Test
    void readsObjectMetadataWithoutOpeningADownloadStream() throws Exception {
        String storageKey = UUID.randomUUID().toString();
        StatObjectResponse response = org.mockito.Mockito.mock(StatObjectResponse.class);
        when(response.contentType()).thenReturn("application/pdf");
        when(response.size()).thenReturn(42L);
        when(minioClient.statObject(any())).thenReturn(response);

        StoredObjectMetadata metadata = service.metadata(storageKey);

        assertThat(metadata.contentType().toString()).isEqualTo("application/pdf");
        assertThat(metadata.sizeBytes()).isEqualTo(42L);
        verify(minioClient).statObject(any());
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile(
                "file",
                name,
                "application/pdf",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
