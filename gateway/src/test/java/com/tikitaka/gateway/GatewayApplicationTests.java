package com.tikitaka.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class GatewayApplicationTests {

	private static final String TEST_ONLY_DUMMY_JWT_SECRET = Base64.getEncoder().encodeToString(
			"dummy-jwt-secret-for-tests-32-bytes".getBytes(StandardCharsets.UTF_8)
	);

	@DynamicPropertySource
	static void registerTestProperties(DynamicPropertyRegistry registry) {
		registry.add("auth.token.secret", () -> TEST_ONLY_DUMMY_JWT_SECRET);
		registry.add("services.platform.url", () -> "http://localhost:8081");
		registry.add("services.ticketing.url", () -> "http://localhost:8082");
		registry.add("services.payment-notification.url", () -> "http://localhost:8083");
	}

	@Test
	void contextLoads() {
	}

}
