package com.tikitaka.ticketing.reservation.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.exception.CommonErrorCode;
import com.tikitaka.ticketing.reservation.application.ReservationPaymentEventService;
import com.tikitaka.ticketing.reservation.application.command.PaymentFailedCommand;
import com.tikitaka.ticketing.reservation.application.command.PaymentSucceededCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PAYMENT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Long USER_ID = 1L;
    private static final Long AMOUNT = 50_000L;

    @Mock
    private ReservationPaymentEventService reservationPaymentEventService;

    private PaymentEventConsumer paymentEventConsumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        paymentEventConsumer = new PaymentEventConsumer(objectMapper, reservationPaymentEventService);
    }

    @Test
    void PAYMENT_SUCCEEDED_JSON을_성공_Command로_변환한다() throws Exception {
        // given
        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "PAYMENT_SUCCEEDED",
                  "occurredAt": "2026-09-06T18:59:00+09:00",
                  "aggregateId": "%s",
                  "version": 1,
                  "paymentId": "%s",
                  "reservationId": "%s",
                  "userId": 1,
                  "amount": 50000,
                  "approvedAt": "2026-09-06T19:00:00+09:00"
                }
                """.formatted(EVENT_ID, RESERVATION_ID, PAYMENT_ID, RESERVATION_ID);
        given(reservationPaymentEventService.processPaymentSucceeded(any(PaymentSucceededCommand.class))).willReturn(true);

        // when
        paymentEventConsumer.consume(payload);

        // then
        ArgumentCaptor<PaymentSucceededCommand> commandCaptor = ArgumentCaptor.forClass(PaymentSucceededCommand.class);
        verify(reservationPaymentEventService).processPaymentSucceeded(commandCaptor.capture());
        PaymentSucceededCommand command = commandCaptor.getValue();
        assertThat(command.getEventId()).isEqualTo(EVENT_ID);
        assertThat(command.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(command.getReservationId()).isEqualTo(RESERVATION_ID);
        assertThat(command.getUserId()).isEqualTo(USER_ID);
        assertThat(command.getAmount()).isEqualTo(AMOUNT);
        assertThat(command.getApprovedAt()).isEqualTo(Instant.parse("2026-09-06T10:00:00Z"));
        verify(reservationPaymentEventService, never()).processPaymentFailed(any(PaymentFailedCommand.class));
    }

    @Test
    void PAYMENT_FAILED_JSON을_실패_Command로_변환한다() throws Exception {
        // given
        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "PAYMENT_FAILED",
                  "occurredAt": "2026-09-06T18:59:00+09:00",
                  "aggregateId": "%s",
                  "version": 1,
                  "paymentId": "%s",
                  "reservationId": "%s",
                  "userId": 1,
                  "amount": 50000,
                  "failureCode": "PAYMENT_DECLINED",
                  "failedAt": "2026-09-06T19:00:00+09:00"
                }
                """.formatted(EVENT_ID, RESERVATION_ID, PAYMENT_ID, RESERVATION_ID);
        given(reservationPaymentEventService.processPaymentFailed(any(PaymentFailedCommand.class))).willReturn(true);

        // when
        paymentEventConsumer.consume(payload);

        // then
        ArgumentCaptor<PaymentFailedCommand> commandCaptor = ArgumentCaptor.forClass(PaymentFailedCommand.class);
        verify(reservationPaymentEventService).processPaymentFailed(commandCaptor.capture());
        PaymentFailedCommand command = commandCaptor.getValue();
        assertThat(command.getEventId()).isEqualTo(EVENT_ID);
        assertThat(command.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(command.getReservationId()).isEqualTo(RESERVATION_ID);
        assertThat(command.getUserId()).isEqualTo(USER_ID);
        assertThat(command.getAmount()).isEqualTo(AMOUNT);
        verify(reservationPaymentEventService, never()).processPaymentSucceeded(any(PaymentSucceededCommand.class));
    }

    @Test
    void 지원하지_않는_eventType이면_Application_Service를_호출하지_않는다() {
        // given
        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "PAYMENT_UNKNOWN"
                }
                """.formatted(EVENT_ID);

        // when
        BusinessException exception = catchThrowableOfType(
                () -> paymentEventConsumer.consume(payload),
                BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNSUPPORTED_REQUEST);
        verify(reservationPaymentEventService, never()).processPaymentSucceeded(any(PaymentSucceededCommand.class));
        verify(reservationPaymentEventService, never()).processPaymentFailed(any(PaymentFailedCommand.class));
    }
}
