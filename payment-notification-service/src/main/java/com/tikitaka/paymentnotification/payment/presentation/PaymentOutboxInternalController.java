package com.tikitaka.paymentnotification.payment.presentation;

import com.tikitaka.paymentnotification.payment.application.PaymentOutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/payments/outboxes")
public class PaymentOutboxInternalController {
    private final PaymentOutboxPublisher paymentOutboxPublisher;

    @PostMapping("/{outboxId}/retry")
    public ResponseEntity<Void> retryFailedOutbox(@PathVariable UUID outboxId) {

        paymentOutboxPublisher.retryFailedOutbox(outboxId);

        return ResponseEntity.noContent().build();
    }
}
