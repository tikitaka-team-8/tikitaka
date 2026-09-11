package com.tikitaka.platform.event.infrastructure.client.ticketing;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;


public class TicketingFeignConfig {

  private final String SERVICE_KEY_HEADER = "X-Service-Key";

  @Bean
  public RequestInterceptor ticketingServiceKeyInterceptor(
      @Value("${clients.ticketing-service.service-key}")
      String serviceKey
  ) {
    if (serviceKey == null || serviceKey.isBlank()) {
      throw new IllegalStateException(
          "Ticketing 내부 서비스 키는 필수입니다."
      );
    }

    return template ->
        template.header(SERVICE_KEY_HEADER, serviceKey);
  }
}
