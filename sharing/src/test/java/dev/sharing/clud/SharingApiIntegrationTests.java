package dev.sharing.clud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
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

import dev.sharing.clud.client.FileNodeResponse;
import dev.sharing.clud.client.FileServiceClient;
import dev.sharing.clud.event.FileEventConsumer;
import dev.sharing.clud.event.FileLifecycleEvent;
import dev.sharing.clud.event.FileSharedEvent;
import dev.sharing.clud.share.entity.ShareLink;
import dev.sharing.clud.share.repository.ShareLinkRepository;
import dev.sharing.clud.share.service.ShareTokenService;

@Testcontainers
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.kafka.listener.auto-startup=false")
class SharingApiIntegrationTests {

	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	private static final Pattern ID_PATTERN = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"");
	private static final Pattern URL_PATTERN = Pattern.compile("\\\"publicUrl\\\":\\\"([^\\\"]+)\\\"");

	@Container
	@ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

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
		HttpResponse<String> pendingDownload = request("GET", "/public/" + secondToken, null, null);
		assertThat(pendingDownload.statusCode()).isEqualTo(501);
		assertThat(pendingDownload.body()).contains("STORAGE_INTEGRATION_PENDING");

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

		CreatedLink created = createLink(ownerId, fileId, null);
		HttpResponse<String> revoked = request(
				"DELETE",
				"/links/" + created.id(),
				ownerId,
				null);
		assertThat(revoked.statusCode()).isEqualTo(204);
		assertThat(request("GET", "/public/" + created.token(), null, null).statusCode()).isEqualTo(404);
	}

	@Test
	void revokesActiveLinkWhenFileIsDeleted() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID fileId = UUID.randomUUID();
		when(fileServiceClient.getActiveFile(ownerId, fileId)).thenReturn(file(fileId));
		createLink(ownerId, fileId, null);

		fileEventConsumer.consume(new FileLifecycleEvent(
				UUID.randomUUID(),
				"FileDeleted",
				1,
				Instant.now(),
				fileId,
				ownerId,
				"objects/" + fileId));

		assertThat(request("GET", "/links?fileId=" + fileId, ownerId, null).statusCode()).isEqualTo(404);
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
		return new FileNodeResponse(
				fileId,
				null,
				FileNodeResponse.NodeType.FILE,
				"report.pdf",
				"objects/" + fileId,
				"application/pdf",
				128L,
				null,
				Instant.now(),
				Instant.now(),
				0);
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
