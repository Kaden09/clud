package dev.identity.clud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:tc:postgresql:17-alpine:///clud",
		"spring.datasource.username=test",
		"spring.datasource.password=test",
		"spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"jwt.secret=Y2x1ZC10ZXN0LWp3dC1zZWNyZXQtMzItYnl0ZXMh"
})
class CludApplicationTests {

	@Test
	void contextLoads() {
	}

}
