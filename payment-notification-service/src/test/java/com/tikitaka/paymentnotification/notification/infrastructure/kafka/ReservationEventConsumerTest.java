package com.tikitaka.paymentnotification.notification.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tikitaka.paymentnotification.global.exception.BusinessException;
import com.tikitaka.paymentnotification.notification.application.service.ReservationNotificationEventService;
import com.tikitaka.paymentnotification.notification.application.command.ReservationConfirmedNotificationCommand;
import com.tikitaka.paymentnotification.notification.application.command.ReservationFailedNotificationCommand;
import com.tikitaka.paymentnotification.notification.exception.NotificationErrorCode;
import com.tikitaka.paymentnotification.notification.infrastructure.kafka.event.ReservationConfirmedEvent;
import com.tikitaka.paymentnotification.notification.infrastructure.kafka.event.ReservationFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReservationEventConsumerTest {

    @Mock
    private ReservationNotificationEventService reservationNotificationEventService;

    private ObjectMapper objectMapper;
    private ReservationEventConsumer reservationEventConsumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        reservationEventConsumer = new ReservationEventConsumer(objectMapper, reservationNotificationEventService);
    }

    @Test
    void 예매_확정_이벤트를_수신하면_확정_Command로_변환한다() throws JsonProcessingException {
        // given
        ReservationConfirmedEvent event = new ReservationConfirmedEvent(
                UUID.randomUUID(), "RESERVATION_CONFIRMED", 1, Instant.parse("2026-09-08T01:00:00Z"),
                UUID.randomUUID(), "RSV-260908-123456789012", 1L, "티키타카 콘서트",
                Instant.parse("2026-09-13T07:30:00Z")
        );
        String payload = objectMapper.writeValueAsString(event);

        // when
        reservationEventConsumer.consume(payload);

        // then
        ArgumentCaptor<ReservationConfirmedNotificationCommand> captor =
                ArgumentCaptor.forClass(ReservationConfirmedNotificationCommand.class);
        verify(reservationNotificationEventService).processReservationConfirmed(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ReservationConfirmedNotificationCommand(
                event.eventId(), event.eventType(), event.eventVersion(), event.occurredAt(), event.reservationId(),
                event.reservationNumber(), event.userId(), event.eventTitle(), event.sessionStartAt()
        ));
    }

    @Test
    void 예매_실패_이벤트를_수신하면_실패_Command로_변환한다() throws JsonProcessingException {
        // given
        ReservationFailedEvent event = new ReservationFailedEvent(
                UUID.randomUUID(), "RESERVATION_FAILED", 1, Instant.parse("2026-09-08T01:00:00Z"),
                UUID.randomUUID(), "RSV-260908-123456789012", 1L, "티키타카 콘서트",
                Instant.parse("2026-09-13T07:30:00Z"), "PAYMENT_FAILED"
        );
        String payload = objectMapper.writeValueAsString(event);

        // when
        reservationEventConsumer.consume(payload);

        // then
        ArgumentCaptor<ReservationFailedNotificationCommand> captor =
                ArgumentCaptor.forClass(ReservationFailedNotificationCommand.class);
        verify(reservationNotificationEventService).processReservationFailed(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ReservationFailedNotificationCommand(
                event.eventId(), event.eventType(), event.eventVersion(), event.occurredAt(), event.reservationId(),
                event.reservationNumber(), event.userId(), event.eventTitle(), event.sessionStartAt(),
                event.failureReason()
        ));
    }

    @Test
    void 지원하지_않는_이벤트_유형이면_예외가_발생한다() {
        // given
        String payload = "{\"eventType\":\"UNKNOWN_EVENT\"}";

        // when
        Throwable exception = catchThrowable(() -> reservationEventConsumer.consume(payload));

        // then
        assertThat(exception)
                .isInstanceOfSatisfying(BusinessException.class,
                        businessException -> assertThat(businessException.getErrorCode())
                                .isEqualTo(NotificationErrorCode.UNSUPPORTED_NOTIFICATION_TYPE));
        verifyNoInteractions(reservationNotificationEventService);
    }

    @Test
    void 올바르지_않은_JSON이면_역직렬화_예외가_전파된다() {
        // given
        String payload = "{invalid-json";

        // when
        Throwable exception = catchThrowable(() -> reservationEventConsumer.consume(payload));

        // then
        assertThat(exception)
                .isInstanceOf(JsonProcessingException.class);
        verifyNoInteractions(reservationNotificationEventService);
    }
}
