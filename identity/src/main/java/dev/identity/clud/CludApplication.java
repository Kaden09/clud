package dev.identity.clud;

import dev.identity.clud.security.jwt.JwtProperties;
import dev.identity.clud.security.CorsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class CludApplication {

    public static void main(String[] args) {
        SpringApplication.run(CludApplication.class, args);
    }
}
