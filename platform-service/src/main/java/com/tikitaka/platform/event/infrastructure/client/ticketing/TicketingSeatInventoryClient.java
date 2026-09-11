package com.tikitaka.platform.event.infrastructure.client.ticketing;

import com.tikitaka.platform.event.infrastructure.client.ticketing.dto.CreateScheduleSeatsRequest;
import com.tikitaka.platform.event.infrastructure.client.ticketing.dto.CreateScheduleSeatsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
    name = "ticketingSeatInventoryClient",
    url = "${clients.ticketing-service.url}",
    configuration = TicketingFeignConfig.class
)
public interface TicketingSeatInventoryClient {

  @PostMapping("/api/v1/internal/event-sessions/seats")
  List<CreateScheduleSeatsResponse> createScheduleSeats(
      @RequestBody List<CreateScheduleSeatsRequest> requests
  );
}
