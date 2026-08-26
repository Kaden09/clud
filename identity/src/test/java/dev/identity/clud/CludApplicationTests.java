package dev.identity.clud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:tc:postgresql:17-alpine:///clud",
		"spring.datasource.username=test",
		"spring.datasource.password=test",
		"spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"jwt.secret=f05493d1eebc788c946effb11957855922b54cfe42c2dd200c320578b2e39838854b49e5abbd13865bf475c92492376974bc63e1fbfc5690efdb664d25b28b5d"
})
class CludApplicationTests {

	@Test
	void contextLoads() {
	}

}
