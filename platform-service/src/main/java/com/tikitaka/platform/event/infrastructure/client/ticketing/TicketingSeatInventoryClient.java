package com.tikitaka.platform.event.infrastructure.client.ticketing;

import com.tikitaka.platform.event.infrastructure.client.ticketing.dto.CreateScheduleSeatsRequest;
import com.tikitaka.platform.event.infrastructure.client.ticketing.dto.CreateScheduleSeatsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(
    name = "ticketingSeatInventoryClient",
    url = "${clients.ticketing-service.url}",
    configuration = TicketingFeignConfig.class
)
public interface TicketingSeatInventoryClient {

  @PostMapping("/api/v1/internal/event-sessions/{eventSessionId}/seats")
  CreateScheduleSeatsResponse createScheduleSeats(
      @PathVariable UUID eventSessionId,
      @RequestBody CreateScheduleSeatsRequest request
  );
}
