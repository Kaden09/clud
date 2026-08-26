package dev.identity.clud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:tc:postgresql:17-alpine:///clud",
		"spring.datasource.username=test",
		"spring.datasource.password=test",
		"spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
		"spring.jpa.hibernate.ddl-auto=validate",
		"jwt.secret=Y2x1ZC1kZXZlbG9wbWVudC1qd3Qtc2VjcmV0LTMyYiE="
})
class CludApplicationTests {

	// Этот запуск подтверждает реальную последовательность Flyway -> Hibernate validate.
	@Test
	void contextLoadsWithFlywayMigrations() {
	}

}
