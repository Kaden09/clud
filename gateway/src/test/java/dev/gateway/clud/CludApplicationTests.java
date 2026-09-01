package dev.gateway.clud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "IDENTITY_SERVICE_URL=http://localhost:8081",
        "FILE_SERVICE_URL=http://localhost:8082",
        "SHARING_SERVICE_URL=http://localhost:8084",
        "JWT_SECRET=Y2x1ZC1kZXZlbG9wbWVudC1qd3Qtc2VjcmV0LTMyYiE="
})
class CludApplicationTests {

    @Test
    void contextLoads() {
    }
}
