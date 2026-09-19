package com.tikitaka.paymentnotification;

import com.tikitaka.paymentnotification.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
		"clients.ticketing-service.url=http://localhost:8082",
		"internal.service.key=test-service-key"
})
@PostgresIntegrationTest
class PaymentNotificationServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
