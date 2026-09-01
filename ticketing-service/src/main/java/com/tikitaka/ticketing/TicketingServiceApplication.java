package com.tikitaka.ticketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TicketingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketingServiceApplication.class, args);
	}

}
