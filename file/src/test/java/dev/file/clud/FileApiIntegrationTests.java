package dev.file.clud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.file.clud.event.FileLifecycleEvent;
import dev.file.clud.node.repository.FileNodeRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FileApiIntegrationTests {

	private static final Pattern ID_PATTERN = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"");
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	@Container
	@ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	@LocalServerPort
	private int port;

	@MockitoBean
	private KafkaTemplate<String, Object> kafkaTemplate;

	private final FileNodeRepository repository;

	@Autowired
	FileApiIntegrationTests(FileNodeRepository repository) {
		this.repository = repository;
	}

	@BeforeEach
	void cleanDatabase() {
		repository.deleteAllInBatch();
	}

	@Test
	void managesDirectoryTreeAndTrash() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID documentsId = createFolder(ownerId, "Documents", null);
		UUID reportsId = createFolder(ownerId, "Reports", documentsId);
		UUID fileId = createFile(ownerId, "report.pdf", reportsId);

		HttpResponse<String> children = request("GET", "/nodes?parentId=" + reportsId, ownerId, null);
		assertThat(children.statusCode()).isEqualTo(200);
		assertThat(children.body()).contains(fileId.toString(), "report.pdf", "FILE");

		HttpResponse<String> renamed = request(
				"PATCH",
				"/nodes/" + fileId,
				ownerId,
				"{\"name\":\"annual-report.pdf\"}");
		assertThat(renamed.statusCode()).isEqualTo(200);
		assertThat(renamed.body()).contains("annual-report.pdf");

		HttpResponse<String> trashed = request("DELETE", "/nodes/" + documentsId, ownerId, null);
		assertThat(trashed.statusCode()).isEqualTo(204);
		assertThat(request("GET", "/nodes/" + fileId, ownerId, null).statusCode()).isEqualTo(404);

		HttpResponse<String> trash = request("GET", "/trash", ownerId, null);
		assertThat(trash.statusCode()).isEqualTo(200);
		assertThat(trash.body()).contains(documentsId.toString()).doesNotContain(reportsId.toString());

		HttpResponse<String> restored = request("POST", "/trash/" + documentsId + "/restore", ownerId, "{}");
		assertThat(restored.statusCode()).isEqualTo(200);
		assertThat(request("GET", "/nodes/" + fileId, ownerId, null).statusCode()).isEqualTo(200);
	}

	@Test
	void isolatesNodesByOwnerAndRejectsNameConflicts() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID otherOwnerId = UUID.randomUUID();
		UUID folderId = createFolder(ownerId, "Private", null);

		assertThat(request("GET", "/nodes/" + folderId, otherOwnerId, null).statusCode()).isEqualTo(404);

		HttpResponse<String> duplicate = request(
				"POST",
				"/folders",
				ownerId,
				"{\"name\":\"private\"}");
		assertThat(duplicate.statusCode()).isEqualTo(409);
		assertThat(duplicate.body()).contains("NODE_CONFLICT");
	}

	@Test
	void rejectsMovingFolderIntoItsDescendant() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID parentId = createFolder(ownerId, "Parent", null);
		UUID childId = createFolder(ownerId, "Child", parentId);

		HttpResponse<String> response = request(
				"POST",
				"/nodes/" + parentId + "/move?targetParentId=" + childId,
				ownerId,
				null);

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.body()).contains("INVALID_NODE_OPERATION");
	}

	@Test
	void publishesVersionedFileEvents() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID fileId = createFile(ownerId, "photo.png", null);

		verify(kafkaTemplate).send(
				eq("file.events.v1"),
				eq(fileId.toString()),
				argThat(value -> value instanceof FileLifecycleEvent event
						&& event.eventType().equals("FileUploaded")
						&& event.eventVersion() == 1));

		request("DELETE", "/nodes/" + fileId, ownerId, null);

		verify(kafkaTemplate).send(
				eq("file.events.v1"),
				eq(fileId.toString()),
				argThat(value -> value instanceof FileLifecycleEvent event
						&& event.eventType().equals("FileDeleted")
						&& event.eventVersion() == 1));
	}

	private UUID createFolder(UUID ownerId, String name, UUID parentId) throws Exception {
		String parent = parentId == null ? "" : ",\"parentId\":\"" + parentId + "\"";
		HttpResponse<String> response = request(
				"POST",
				"/folders",
				ownerId,
				"{\"name\":\"" + name + "\"" + parent + "}");
		assertThat(response.statusCode()).isEqualTo(201);
		return extractId(response.body());
	}

	private UUID createFile(UUID ownerId, String name, UUID parentId) throws Exception {
		String parent = parentId == null ? "" : ",\"parentId\":\"" + parentId + "\"";
		HttpResponse<String> response = request(
				"POST",
				"/files",
				ownerId,
				"{\"name\":\"" + name + "\"" + parent
						+ ",\"storageKey\":\"objects/" + UUID.randomUUID() + "\""
						+ ",\"contentType\":\"application/octet-stream\",\"sizeBytes\":128}");
		assertThat(response.statusCode()).isEqualTo(201);
		return extractId(response.body());
	}

	private HttpResponse<String> request(String method, String path, UUID ownerId, String body)
			throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("X-User-ID", ownerId.toString());
		if (body != null) {
			builder.header("Content-Type", "application/json");
		}
		HttpRequest.BodyPublisher publisher = body == null
				? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(body);
		return HTTP_CLIENT.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
	}

	private UUID extractId(String body) {
		Matcher matcher = ID_PATTERN.matcher(body);
		assertThat(matcher.find()).isTrue();
		return UUID.fromString(matcher.group(1));
	}
}
