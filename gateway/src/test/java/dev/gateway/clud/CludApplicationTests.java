package dev.gateway.clud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"IDENTITY_SERVICE_URL=http://localhost:8081",
		"FILE_SERVICE_URL=http://localhost:8082",
		"STORAGE_SERVICE_URL=http://localhost:8083",
		"SHARING_SERVICE_URL=http://localhost:8084"
})
class CludApplicationTests {

	@Test
	void contextLoads() {
	}

}
