package dev.sharing.clud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicReference;

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

import dev.sharing.clud.client.FileNodeResponse;
import dev.sharing.clud.client.FileServiceClient;
import dev.sharing.clud.event.FileEventConsumer;
import dev.sharing.clud.event.FileLifecycleEvent;
import dev.sharing.clud.event.FileSharedEvent;
import dev.sharing.clud.share.entity.ShareLink;
import dev.sharing.clud.share.repository.ShareLinkRepository;
import dev.sharing.clud.share.service.ShareTokenService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@Testcontainers
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.kafka.listener.auto-startup=false")
class SharingApiIntegrationTests {

	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	private static final Pattern ID_PATTERN = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"");
	private static final Pattern URL_PATTERN = Pattern.compile("\\\"publicUrl\\\":\\\"([^\\\"]+)\\\"");
	private static final byte[] SHARED_CONTENT = "%PDF-shared-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
	private static final String MISSING_STORAGE_KEY = "00000000-0000-0000-0000-000000000000";
	private static final AtomicReference<String> LAST_STORAGE_KEY = new AtomicReference<>();
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
	private FileServiceClient fileServiceClient;

	@MockitoBean
	private KafkaTemplate<String, Object> kafkaTemplate;

	private final ShareLinkRepository repository;
	private final ShareTokenService tokenService;
	private final FileEventConsumer fileEventConsumer;

	@Autowired
	SharingApiIntegrationTests(
			ShareLinkRepository repository,
			ShareTokenService tokenService,
			FileEventConsumer fileEventConsumer) {
		this.repository = repository;
		this.tokenService = tokenService;
		this.fileEventConsumer = fileEventConsumer;
	}

	@BeforeEach
	void cleanDatabase() {
		repository.deleteAllInBatch();
		LAST_STORAGE_KEY.set(null);
	}

	@AfterAll
	static void stopStorage() {
		STORAGE.stop(0);
	}

	@Test
	void rotatesTheOnlyActiveLinkAndPublishesFileShared() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID fileId = UUID.randomUUID();
		when(fileServiceClient.getActiveFile(ownerId, fileId)).thenReturn(file(fileId));

		CreatedLink first = createLink(ownerId, fileId, null);
		String firstToken = first.token();
		CreatedLink second = createLink(ownerId, fileId, null);
		String secondToken = second.token();

		assertThat(firstToken).isNotEqualTo(secondToken);
		assertThat(request("GET", "/public/" + firstToken, null, null).statusCode()).isEqualTo(404);
		HttpResponse<String> metadata = request("GET", "/public/" + secondToken, null, null);
		assertThat(metadata.statusCode()).isEqualTo(200);
		assertThat(metadata.body())
				.contains("\"previewAvailable\":true", "/preview", "/download", "report.pdf")
				.doesNotContain("storageKey", storageKey(fileId));

		HttpResponse<String> active = request("GET", "/links?fileId=" + fileId, ownerId, null);
		assertThat(active.statusCode()).isEqualTo(200);
		assertThat(active.body()).contains(second.id().toString()).doesNotContain(firstToken, secondToken);
		assertThat(repository.findAll())
				.hasSize(2)
				.allSatisfy(link -> assertThat(link.getTokenHash()).doesNotContain(firstToken, secondToken));

		verify(kafkaTemplate, times(2)).send(
				eq("sharing.events.v1"),
				eq(fileId.toString()),
				argThat(value -> value instanceof FileSharedEvent event
						&& event.eventType().equals("FileShared")
						&& event.eventVersion() == 1));
	}

	@Test
	void rejectsExpiredAndRevokedLinks() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID fileId = UUID.randomUUID();
		when(fileServiceClient.getActiveFile(ownerId, fileId)).thenReturn(file(fileId));

		ShareTokenService.GeneratedToken expiredToken = tokenService.generate();
		repository.saveAndFlush(ShareLink.create(
				ownerId,
				fileId,
				expiredToken.hash(),
				Instant.now().minusSeconds(1)));
		assertThat(request("GET", "/public/" + expiredToken.value(), null, null).statusCode()).isEqualTo(410);
		assertThat(request("GET", "/public/" + expiredToken.value() + "/preview", null, null).statusCode())
				.isEqualTo(410);

		CreatedLink created = createLink(ownerId, fileId, null);
		HttpResponse<String> revoked = request(
				"DELETE",
				"/links/" + created.id(),
				ownerId,
				null);
		assertThat(revoked.statusCode()).isEqualTo(204);
		assertThat(request("GET", "/public/" + created.token(), null, null).statusCode()).isEqualTo(404);
		assertThat(request("GET", "/public/" + created.token() + "/download", null, null).statusCode())
				.isEqualTo(404);
	}

	@Test
	void revokesActiveLinkWhenFileIsDeleted() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID fileId = UUID.randomUUID();
		when(fileServiceClient.getActiveFile(ownerId, fileId)).thenReturn(file(fileId));
		CreatedLink created = createLink(ownerId, fileId, null);

		fileEventConsumer.consume(new FileLifecycleEvent(
				UUID.randomUUID(),
				"FileDeleted",
				1,
				Instant.now(),
				fileId,
				ownerId,
				"objects/" + fileId));

		assertThat(request("GET", "/links?fileId=" + fileId, ownerId, null).statusCode()).isEqualTo(404);
		assertThat(request("GET", "/public/" + created.token(), null, null).statusCode()).isEqualTo(404);
	}

	@Test
	void previewsInlineAndDownloadsAsAttachmentWithoutExposingStorageKey() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID fileId = UUID.randomUUID();
		when(fileServiceClient.getActiveFile(ownerId, fileId)).thenReturn(file(fileId));
		CreatedLink created = createLink(ownerId, fileId, null);

		HttpResponse<String> preview = request("GET", "/public/" + created.token() + "/preview", null, null);
		assertThat(preview.statusCode()).isEqualTo(200);
		assertThat(preview.body()).isEqualTo("%PDF-shared-content");
		assertThat(preview.headers().firstValue("Content-Type")).contains("application/pdf");
		assertThat(preview.headers().firstValue("Content-Disposition")).hasValueSatisfying(
				value -> assertThat(value).contains("inline", "report.pdf"));
		assertThat(preview.headers().firstValue("X-Content-Type-Options")).contains("nosniff");

		HttpResponse<String> download = request("GET", "/public/" + created.token() + "/download", null, null);
		assertThat(download.statusCode()).isEqualTo(200);
		assertThat(download.body()).isEqualTo("%PDF-shared-content");
		assertThat(download.headers().firstValue("Content-Disposition")).hasValueSatisfying(
				value -> assertThat(value).contains("attachment", "report.pdf"));
		assertThat(LAST_STORAGE_KEY).hasValue(storageKey(fileId));
	}

	@Test
	void blocksUnsafeInlineTypesButStillAllowsDownload() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID fileId = UUID.randomUUID();
		when(fileServiceClient.getActiveFile(ownerId, fileId)).thenReturn(
				file(fileId, "page.html", "text/html"));
		CreatedLink created = createLink(ownerId, fileId, null);

		HttpResponse<String> metadata = request("GET", "/public/" + created.token(), null, null);
		assertThat(metadata.statusCode()).isEqualTo(200);
		assertThat(metadata.body()).contains("\"previewAvailable\":false");

		HttpResponse<String> preview = request("GET", "/public/" + created.token() + "/preview", null, null);
		assertThat(preview.statusCode()).isEqualTo(415);
		assertThat(preview.body()).contains("PREVIEW_NOT_SUPPORTED");
		assertThat(request("GET", "/public/" + created.token() + "/download", null, null).statusCode())
				.isEqualTo(200);
	}

	@Test
	void returnsNotFoundBeforeStartingAResponseWhenStorageObjectIsMissing() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID fileId = UUID.randomUUID();
		when(fileServiceClient.getActiveFile(ownerId, fileId)).thenReturn(
				file(fileId, "missing.pdf", "application/pdf", MISSING_STORAGE_KEY));
		CreatedLink created = createLink(ownerId, fileId, null);

		HttpResponse<String> download = request(
				"GET",
				"/public/" + created.token() + "/download",
				null,
				null);

		assertThat(download.statusCode()).isEqualTo(404);
		assertThat(download.body()).contains("SHARED_FILE_NOT_FOUND");
		assertThat(LAST_STORAGE_KEY).hasValue(MISSING_STORAGE_KEY);
	}

	private CreatedLink createLink(UUID ownerId, UUID fileId, Instant expiresAt) throws Exception {
		String expiration = expiresAt == null ? "" : ",\"expiresAt\":\"" + expiresAt + "\"";
		HttpResponse<String> response = request(
				"POST",
				"/links",
				ownerId,
				"{\"fileId\":\"" + fileId + "\"" + expiration + "}");
		assertThat(response.statusCode()).isEqualTo(201);
		Matcher idMatcher = ID_PATTERN.matcher(response.body());
		Matcher urlMatcher = URL_PATTERN.matcher(response.body());
		assertThat(idMatcher.find()).isTrue();
		assertThat(urlMatcher.find()).isTrue();
		String publicUrl = urlMatcher.group(1);
		return new CreatedLink(
				UUID.fromString(idMatcher.group(1)),
				publicUrl.substring(publicUrl.lastIndexOf('/') + 1));
	}

	private FileNodeResponse file(UUID fileId) {
		return file(fileId, "report.pdf", "application/pdf");
	}

	private FileNodeResponse file(UUID fileId, String name, String contentType) {
		return file(fileId, name, contentType, storageKey(fileId));
	}

	private FileNodeResponse file(UUID fileId, String name, String contentType, String storageKey) {
		return new FileNodeResponse(
				fileId,
				null,
				FileNodeResponse.NodeType.FILE,
				name,
				storageKey,
				contentType,
				(long) SHARED_CONTENT.length,
				null,
				Instant.now(),
				Instant.now(),
				0);
	}

	private String storageKey(UUID fileId) {
		return UUID.nameUUIDFromBytes(
				("storage:" + fileId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
	}

	private static HttpServer startStorage() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/objects", SharingApiIntegrationTests::handleStorage);
			server.start();
			return server;
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not start fake Storage Service", exception);
		}
	}

	private static void handleStorage(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		String storageKey = path.substring(path.lastIndexOf('/') + 1);
		LAST_STORAGE_KEY.set(storageKey);
		if (MISSING_STORAGE_KEY.equals(storageKey)) {
			exchange.sendResponseHeaders(404, -1);
			exchange.close();
			return;
		}
		exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
		exchange.getResponseHeaders().set("Content-Length", Integer.toString(SHARED_CONTENT.length));
		if ("HEAD".equals(exchange.getRequestMethod())) {
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
			return;
		}
		exchange.sendResponseHeaders(200, SHARED_CONTENT.length);
		exchange.getResponseBody().write(SHARED_CONTENT);
		exchange.close();
	}

	private HttpResponse<String> request(String method, String path, UUID ownerId, String body)
			throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
		if (ownerId != null) {
			builder.header("X-User-ID", ownerId.toString());
		}
		if (body != null) {
			builder.header("Content-Type", "application/json");
		}
		HttpRequest.BodyPublisher publisher = body == null
				? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(body);
		return HTTP_CLIENT.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
	}

	private record CreatedLink(UUID id, String token) {
	}
}
