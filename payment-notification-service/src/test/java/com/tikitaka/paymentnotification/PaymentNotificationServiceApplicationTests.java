package com.tikitaka.paymentnotification;

import org.junit.jupiter.api.Test;
import com.tikitaka.paymentnotification.testsupport.PostgresIntegrationTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"clients.ticketing-service.url=http://localhost:8082",
		"clients.ticketing-service.service-key=test-service-key"
})
@PostgresIntegrationTest
class PaymentNotificationServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
