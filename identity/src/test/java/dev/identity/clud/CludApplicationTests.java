package dev.identity.clud;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"spring.datasource.url=jdbc:tc:postgresql:17-alpine:///clud",
				"spring.datasource.username=test",
				"spring.datasource.password=test",
				"spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
				"spring.jpa.hibernate.ddl-auto=validate",
				"jwt.secret=Y2x1ZC1kZXZlbG9wbWVudC1qd3Qtc2VjcmV0LTMyYiE="
		})
class CludApplicationTests {

	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	@LocalServerPort
	private int port;

	// Этот запуск подтверждает реальную последовательность Flyway -> Hibernate validate.
	@Test
	void contextLoadsWithFlywayMigrations() {
	}

	@Test
	void exposesAuthWithoutAdditionalContextPath() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(uri("/auth/login"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{}"))
				.build();

		HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(400);
	}

	@Test
	void exposesReadinessWithoutAdditionalContextPath() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(uri("/actuator/health/readiness"))
				.GET()
				.build();

		HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"status\":\"UP\"");
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}
}
