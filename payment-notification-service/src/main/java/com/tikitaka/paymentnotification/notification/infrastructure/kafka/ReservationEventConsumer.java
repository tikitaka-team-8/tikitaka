package com.tikitaka.paymentnotification.notification.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.paymentnotification.global.exception.BusinessException;
import com.tikitaka.paymentnotification.notification.application.ReservationNotificationEventService;
import com.tikitaka.paymentnotification.notification.application.command.ReservationConfirmedNotificationCommand;
import com.tikitaka.paymentnotification.notification.application.command.ReservationFailedNotificationCommand;
import com.tikitaka.paymentnotification.notification.exception.NotificationErrorCode;
import com.tikitaka.paymentnotification.notification.infrastructure.kafka.event.ReservationConfirmedEvent;
import com.tikitaka.paymentnotification.notification.infrastructure.kafka.event.ReservationFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReservationEventConsumer {
    private static final String RESERVATION_CONFIRMED = "RESERVATION_CONFIRMED";
    private static final String RESERVATION_FAILED = "RESERVATION_FAILED";

    private final ObjectMapper objectMapper;
    private final ReservationNotificationEventService reservationNotificationEventService;

    public ReservationEventConsumer(ObjectMapper objectMapper, ReservationNotificationEventService reservationNotificationEventService) {
        this.objectMapper = objectMapper;
        this.reservationNotificationEventService = reservationNotificationEventService;
    }

    @KafkaListener(topics = KafkaTopics.RESERVATION_EVENTS)
    public void consume(String payload) throws JsonProcessingException {

        // 같은 토픽의 예매 이벤트를 eventType으로 구분
        JsonNode eventJson = objectMapper.readTree(payload);
        String eventType = eventJson.path("eventType").asText();

        // 이벤트 종류에 맞는 DTO와 Command로 변환하여 Application Service 호출
        switch (eventType) {
            case RESERVATION_CONFIRMED -> processReservationConfirmed(eventJson);
            case RESERVATION_FAILED -> processReservationFailed(eventJson);
            default -> throw new BusinessException(NotificationErrorCode.UNSUPPORTED_NOTIFICATION_TYPE);
        }
    }

    private void processReservationConfirmed(JsonNode eventJson) throws JsonProcessingException {

        ReservationConfirmedEvent event = objectMapper.treeToValue(eventJson, ReservationConfirmedEvent.class);

        boolean notificationCreated = reservationNotificationEventService.processReservationConfirmed(
                new ReservationConfirmedNotificationCommand(
                        event.eventId(), event.eventType(), event.eventVersion(), event.occurredAt(), event.reservationId(),
                        event.reservationNumber(), event.userId(), event.eventTitle(), event.sessionStartAt()
                )
        );

        log.info("ReservationConfirmedEvent 처리 완료: eventId={}, reservationId={}, notificationCreated={}",
                event.eventId(), event.reservationId(), notificationCreated);
    }

    private void processReservationFailed(JsonNode eventJson) throws JsonProcessingException {

        ReservationFailedEvent event = objectMapper.treeToValue(eventJson, ReservationFailedEvent.class);

        boolean notificationCreated = reservationNotificationEventService.processReservationFailed(
                new ReservationFailedNotificationCommand(
                        event.eventId(), event.eventType(), event.eventVersion(), event.occurredAt(), event.reservationId(),
                        event.reservationNumber(), event.userId(), event.eventTitle(), event.sessionStartAt(), event.failureReason()
                )
        );

        log.info("ReservationFailedEvent 처리 완료: eventId={}, reservationId={}, notificationCreated={}",
                event.eventId(), event.reservationId(), notificationCreated);
    }
}
