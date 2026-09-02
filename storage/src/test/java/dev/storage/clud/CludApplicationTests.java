package dev.storage.clud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "MINIO_ROOT_USER=minio",
        "MINIO_ROOT_PASSWORD=minioadmin"
})
class CludApplicationTests {

	@Test
	void contextLoads() {
	}

}
