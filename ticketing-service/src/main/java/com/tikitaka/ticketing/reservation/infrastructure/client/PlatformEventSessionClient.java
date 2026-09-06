package com.tikitaka.ticketing.reservation.infrastructure.client;

import com.tikitaka.ticketing.reservation.domain.model.ReservationEventSessionInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(
        name = "platformEventSessionClient",
        url = "${clients.platform-service.url}"
)
public interface PlatformEventSessionClient {

    @GetMapping("/api/v1/internal/event-sessions/{eventSessionId}/reservation-info")
    ReservationEventSessionInfo getReservationInfo(@PathVariable UUID eventSessionId, @RequestHeader("X-Service-Key") String serviceKey);
}
