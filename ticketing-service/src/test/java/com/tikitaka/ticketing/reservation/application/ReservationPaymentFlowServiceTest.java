package com.tikitaka.ticketing.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.tikitaka.ticketing.reservation.application.command.PaymentFailedCommand;
import com.tikitaka.ticketing.reservation.application.command.PaymentSucceededCommand;
import com.tikitaka.ticketing.reservation.application.result.ReservationPaymentEventResult;
import com.tikitaka.ticketing.reservation.application.service.ReservationPaymentEventService;
import com.tikitaka.ticketing.reservation.application.service.ReservationPaymentFlowService;
import com.tikitaka.ticketing.reservation.domain.port.ReservationQueueFlowPort;
import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.queue.exception.QueueErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationPaymentFlowServiceTest {
    private static final UUID EVENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PAYMENT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_SESSION_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final long USER_ID = 1L;
    private static final long AMOUNT = 50_000L;

    @Mock
    private ReservationPaymentEventService reservationPaymentEventService;

    @Mock
    private ReservationQueueFlowPort reservationQueueFlowPort;

    private ReservationPaymentFlowService reservationPaymentFlowService;

    @BeforeEach
    void setUp() {
        reservationPaymentFlowService = new ReservationPaymentFlowService(
                reservationPaymentEventService, reservationQueueFlowPort
        );
    }

    @Test
    void 결제_성공_반영_후_Queue_예매_흐름을_종료한다() {
        PaymentSucceededCommand command = succeededCommand();
        given(reservationPaymentEventService.processPaymentSucceeded(command))
                .willReturn(result(true));

        boolean statusChanged = reservationPaymentFlowService.processPaymentSucceeded(command);

        assertThat(statusChanged).isTrue();
        verify(reservationQueueFlowPort)
                .completeReservationFlow(EVENT_SESSION_ID, USER_ID, RESERVATION_ID);
    }

    @Test
    void 중복_결제_이벤트도_Queue_종료를_다시_요청한다() {
        PaymentFailedCommand command = failedCommand();
        given(reservationPaymentEventService.processPaymentFailed(command))
                .willReturn(result(false));

        boolean statusChanged = reservationPaymentFlowService.processPaymentFailed(command);

        assertThat(statusChanged).isFalse();
        verify(reservationQueueFlowPort)
                .completeReservationFlow(EVENT_SESSION_ID, USER_ID, RESERVATION_ID);
    }

    @Test
    void Queue_종료가_실패하면_예외를_전파해_Kafka_재시도를_허용한다() {
        PaymentSucceededCommand command = succeededCommand();
        given(reservationPaymentEventService.processPaymentSucceeded(command))
                .willReturn(result(true));
        willThrow(new BusinessException(QueueErrorCode.QUEUE_SERVICE_UNAVAILABLE))
                .given(reservationQueueFlowPort)
                .completeReservationFlow(EVENT_SESSION_ID, USER_ID, RESERVATION_ID);

        assertThatThrownBy(() -> reservationPaymentFlowService.processPaymentSucceeded(command))
                .isInstanceOf(BusinessException.class);
    }

    private ReservationPaymentEventResult result(boolean statusChanged) {
        return new ReservationPaymentEventResult(
                statusChanged, RESERVATION_ID, EVENT_SESSION_ID, USER_ID
        );
    }

    private PaymentSucceededCommand succeededCommand() {
        return new PaymentSucceededCommand(
                EVENT_ID, PAYMENT_ID, RESERVATION_ID, USER_ID, AMOUNT, Instant.parse("2026-09-06T10:00:00Z")
        );
    }

    private PaymentFailedCommand failedCommand() {
        return new PaymentFailedCommand(EVENT_ID, PAYMENT_ID, RESERVATION_ID, USER_ID, AMOUNT);
    }
}
