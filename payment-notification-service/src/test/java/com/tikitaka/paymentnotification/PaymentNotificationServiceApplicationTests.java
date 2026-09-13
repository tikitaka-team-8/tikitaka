package com.tikitaka.paymentnotification;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentQueryGateway;
import org.junit.jupiter.api.Test;
import com.tikitaka.paymentnotification.testsupport.PostgresIntegrationTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"clients.ticketing-service.url=http://localhost:8082",
		"internal.service.key=test-service-key"
})
@PostgresIntegrationTest
class PaymentNotificationServiceApplicationTests {

	@MockitoBean
	private PaymentQueryGateway paymentQueryGateway;

	@Test
	void contextLoads() {
	}

}
