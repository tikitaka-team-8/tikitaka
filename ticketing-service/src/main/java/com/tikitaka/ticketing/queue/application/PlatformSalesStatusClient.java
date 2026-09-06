package com.tikitaka.ticketing.queue.application;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "platformSalesStatusClient",
        url = "${clients.platform-service.url}",
        configuration = PlatformSalesStatusClientConfiguration.class
)
public interface PlatformSalesStatusClient {

    @GetMapping("/api/v1/internal/event-sessions/{sessionId}/sales-status")
    PlatformSalesStatus getSalesStatus(@PathVariable UUID sessionId);
}
