package com.tikitaka.platform.event.presentation;

import com.tikitaka.platform.event.application.EventSessionService;
import com.tikitaka.platform.event.presentation.dto.EventSessionInfoResponse;
import com.tikitaka.platform.event.presentation.dto.QueueSalesStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/event-sessions")
public class InternalEventSessionController {

  private final EventSessionService eventSessionService;

  @GetMapping("/{sessionId}/sales-status")
  public QueueSalesStatusResponse getQueueSalseStatus(
      @PathVariable UUID sessionId
  ) {
    return eventSessionService.getQueueSalesStatus(sessionId);
  }

  @GetMapping("/{eventSessionId}/reservation-info")
  public EventSessionInfoResponse getReservationInfo(
      @PathVariable UUID eventSessionId
  ) {
    return eventSessionService.getReservationInfo(eventSessionId);
  }
}