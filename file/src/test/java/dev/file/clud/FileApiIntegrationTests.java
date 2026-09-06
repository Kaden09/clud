package dev.file.clud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.file.clud.event.FileLifecycleEvent;
import dev.file.clud.node.repository.FileNodeRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FileApiIntegrationTests {

	private static final Pattern ID_PATTERN = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"");
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	private static final byte[] STORED_CONTENT = "stored-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
	private static final Map<String, byte[]> OBJECTS = new ConcurrentHashMap<>();
	private static final AtomicInteger DELETE_CALLS = new AtomicInteger();
	private static final HttpServer STORAGE = startStorage();

	@Container
	@ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("STORAGE_SERVICE_URL", () -> "http://localhost:" + STORAGE.getAddress().getPort());
	}

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
		OBJECTS.clear();
		DELETE_CALLS.set(0);
	}

	@AfterAll
	static void stopStorage() {
		STORAGE.stop(0);
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
		assertThat(duplicate.body()).contains("\"code\":\"CONFLICT\"", "already exists");
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
		assertThat(response.body()).contains("\"code\":\"BAD_REQUEST\"", "A folder cannot be moved");
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

	@Test
	void downloadsByFileIdAndKeepsObjectWhileFileIsInTrash() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID fileId = createFile(ownerId, "photo.png", null);

		HttpResponse<String> download = request("GET", "/files/" + fileId + "/content", ownerId, null);
		assertThat(download.statusCode()).isEqualTo(200);
		assertThat(download.body()).isEqualTo("stored-content");
		assertThat(download.headers().firstValue("Content-Disposition")).hasValueSatisfying(
				value -> assertThat(value).contains("attachment", "photo.png"));
		assertThat(request(
				"GET",
				"/files/" + fileId + "/content",
				UUID.randomUUID(),
				null).statusCode()).isEqualTo(404);

		assertThat(request("DELETE", "/nodes/" + fileId, ownerId, null).statusCode()).isEqualTo(204);
		assertThat(request("GET", "/files/" + fileId + "/content", ownerId, null).statusCode()).isEqualTo(404);
		assertThat(OBJECTS).hasSize(1);
		assertThat(DELETE_CALLS).hasValue(0);

		assertThat(request("POST", "/trash/" + fileId + "/restore", ownerId, "{}").statusCode()).isEqualTo(200);
		assertThat(request("GET", "/files/" + fileId + "/content", ownerId, null).statusCode()).isEqualTo(200);
	}

	@Test
	void removesStoredObjectWhenMetadataPersistenceFails() throws Exception {
		UUID ownerId = UUID.randomUUID();

		HttpResponse<String> response = uploadFile(ownerId, "broken.bin", null);

		assertThat(response.statusCode()).isEqualTo(409);
		assertThat(OBJECTS).isEmpty();
		assertThat(DELETE_CALLS).hasValue(1);
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
		HttpResponse<String> response = uploadFile(ownerId, name, parentId);
		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(response.body()).doesNotContain("storageKey");
		return extractId(response.body());
	}

	private HttpResponse<String> uploadFile(UUID ownerId, String name, UUID parentId) throws Exception {
		String boundary = "clud-" + UUID.randomUUID();
		String body = "--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"file\"; filename=\"" + name + "\"\r\n"
				+ "Content-Type: application/octet-stream\r\n\r\n"
				+ "uploaded-content\r\n"
				+ "--" + boundary + "--\r\n";
		String path = "/files" + (parentId == null ? "" : "?parentId=" + parentId);
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("X-User-ID", ownerId.toString())
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private static HttpServer startStorage() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/objects", FileApiIntegrationTests::handleStorage);
			server.start();
			return server;
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not start fake Storage Service", exception);
		}
	}

	private static void handleStorage(HttpExchange exchange) throws IOException {
		String method = exchange.getRequestMethod();
		String path = exchange.getRequestURI().getPath();
		if ("POST".equals(method) && "/objects".equals(path)) {
			String upload = new String(
					exchange.getRequestBody().readAllBytes(),
					java.nio.charset.StandardCharsets.UTF_8);
			String storageKey = UUID.randomUUID().toString();
			OBJECTS.put(storageKey, STORED_CONTENT);
			long storedSize = upload.contains("broken.bin") ? -1 : STORED_CONTENT.length;
			byte[] response = ("{\"storageKey\":\"" + storageKey
					+ "\",\"contentType\":\"application/octet-stream\",\"sizeBytes\":"
					+ storedSize + "}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(201, response.length);
			exchange.getResponseBody().write(response);
		}
		else {
			String storageKey = path.substring(path.lastIndexOf('/') + 1);
			if ("GET".equals(method) && OBJECTS.containsKey(storageKey)) {
				byte[] response = OBJECTS.get(storageKey);
				exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
				exchange.sendResponseHeaders(200, response.length);
				exchange.getResponseBody().write(response);
			}
			else if ("DELETE".equals(method) && OBJECTS.remove(storageKey) != null) {
				DELETE_CALLS.incrementAndGet();
				exchange.sendResponseHeaders(204, -1);
			}
			else {
				exchange.sendResponseHeaders(404, -1);
			}
		}
		exchange.close();
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
