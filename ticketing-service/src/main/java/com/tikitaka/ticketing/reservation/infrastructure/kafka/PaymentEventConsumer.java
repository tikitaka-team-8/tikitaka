package com.tikitaka.ticketing.reservation.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.exception.CommonErrorCode;
import com.tikitaka.ticketing.reservation.application.ReservationPaymentEventService;
import com.tikitaka.ticketing.reservation.application.command.PaymentFailedCommand;
import com.tikitaka.ticketing.reservation.application.command.PaymentSucceededCommand;
import com.tikitaka.ticketing.reservation.infrastructure.kafka.event.PaymentFailedEvent;
import com.tikitaka.ticketing.reservation.infrastructure.kafka.event.PaymentSucceededEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentEventConsumer {
    private static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    private static final String PAYMENT_FAILED = "PAYMENT_FAILED";

    private final ObjectMapper objectMapper;
    private final ReservationPaymentEventService reservationPaymentEventService;

    public PaymentEventConsumer(ObjectMapper objectMapper, ReservationPaymentEventService reservationPaymentEventService) {
        this.objectMapper = objectMapper;
        this.reservationPaymentEventService = reservationPaymentEventService;
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS)
    public void consume(String payload) throws JsonProcessingException {

        // 같은 토픽의 결제 이벤트를 eventType으로 구분
        JsonNode eventJson = objectMapper.readTree(payload);
        String eventType = eventJson.path("eventType").asText();

        // 이벤트 종류에 맞는 DTO로 변환하여 Application Service 호출
        switch (eventType) {
            case PAYMENT_SUCCEEDED -> processPaymentSucceeded(eventJson);
            case PAYMENT_FAILED -> processPaymentFailed(eventJson);
            default -> throw new BusinessException(CommonErrorCode.UNSUPPORTED_REQUEST);
        }
    }

    private void processPaymentSucceeded(JsonNode eventJson) throws JsonProcessingException {
        PaymentSucceededEvent event = objectMapper.treeToValue(eventJson, PaymentSucceededEvent.class);

        boolean statusChanged = reservationPaymentEventService.processPaymentSucceeded(
                new PaymentSucceededCommand(
                        event.eventId(), event.paymentId(), event.reservationId(), event.userId(), event.amount(),
                        event.approvedAt() == null ? null : event.approvedAt().toInstant()
                )
        );

        log.info("PaymentSucceededEvent 처리 완료: eventId={}, reservationId={}, statusChanged={}",
                event.eventId(), event.reservationId(), statusChanged);
    }

    private void processPaymentFailed(JsonNode eventJson) throws JsonProcessingException {
        PaymentFailedEvent event = objectMapper.treeToValue(eventJson, PaymentFailedEvent.class);
        boolean statusChanged = reservationPaymentEventService.processPaymentFailed(
                new PaymentFailedCommand(
                        event.eventId(), event.paymentId(), event.reservationId(), event.userId(), event.amount()
                )
        );

        log.info("PaymentFailedEvent 처리 완료: eventId={}, reservationId={}, failureCode={}, statusChanged={}",
                event.eventId(), event.reservationId(), event.failureCode(), statusChanged);
    }
}
