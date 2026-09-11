package com.tikitaka.paymentnotification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@ConfigurationPropertiesScan
public class PaymentNotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentNotificationServiceApplication.class, args);
	}

}
