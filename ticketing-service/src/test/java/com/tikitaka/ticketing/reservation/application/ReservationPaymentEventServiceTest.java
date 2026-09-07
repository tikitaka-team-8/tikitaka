package com.tikitaka.ticketing.reservation.application;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.exception.CommonErrorCode;
import com.tikitaka.ticketing.reservation.application.command.PaymentFailedCommand;
import com.tikitaka.ticketing.reservation.application.command.PaymentSucceededCommand;
import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.entity.ReservationInbox;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationFailureReason;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import com.tikitaka.ticketing.reservation.domain.port.ReservationInboxRepositoryPort;
import com.tikitaka.ticketing.reservation.domain.port.ReservationRepositoryPort;
import com.tikitaka.ticketing.seat.application.service.SeatHoldReservationValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReservationPaymentEventServiceTest {

    private static final UUID EVENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PAYMENT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_SESSION_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Long USER_ID = 1L;
    private static final Long AMOUNT = 50_000L;
    private static final Instant APPROVED_AT = Instant.parse("2026-09-06T10:00:00Z");

    @Mock
    private ReservationRepositoryPort reservationRepositoryPort;

    @Mock
    private ReservationInboxRepositoryPort reservationInboxRepositoryPort;

    @Mock
    private SeatHoldReservationValidator seatHoldReservationValidator;

    @InjectMocks
    private ReservationPaymentEventService reservationPaymentEventService;

    @Test
    void 결제_성공_이벤트를_처리하면_예매를_확정하고_Inbox를_저장한다() {
        // given
        Reservation reservation = createPaymentProcessingReservation();
        PaymentSucceededCommand command = createPaymentSucceededCommand(EVENT_ID, AMOUNT);

        given(reservationInboxRepositoryPort.existsByEventId(EVENT_ID)).willReturn(false);
        given(reservationRepositoryPort.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));

        // when
        boolean statusChanged = reservationPaymentEventService.processPaymentSucceeded(command);

        // then
        assertThat(statusChanged).isTrue();
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getPaymentCompletedAt()).isEqualTo(APPROVED_AT);
        assertThat(reservation.getUpdatedBy()).isZero();

        ArgumentCaptor<ReservationInbox> inboxCaptor = ArgumentCaptor.forClass(ReservationInbox.class);
        verify(reservationInboxRepositoryPort).save(inboxCaptor.capture());
        assertThat(inboxCaptor.getValue().getEventId()).isEqualTo(EVENT_ID);
        assertThat(inboxCaptor.getValue().getReservationId()).isEqualTo(RESERVATION_ID);
        assertThat(inboxCaptor.getValue().getEventType()).isEqualTo("PAYMENT_SUCCEEDED");
    }

    @Test
    void 결제_실패_이벤트를_처리하면_예매를_실패로_변경하고_Inbox를_저장한다() {
        // given
        Reservation reservation = createPaymentProcessingReservation();
        PaymentFailedCommand command = createPaymentFailedCommand(EVENT_ID, AMOUNT);

        given(reservationInboxRepositoryPort.existsByEventId(EVENT_ID)).willReturn(false);
        given(reservationRepositoryPort.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));

        // when
        boolean statusChanged = reservationPaymentEventService.processPaymentFailed(command);

        // then
        assertThat(statusChanged).isTrue();
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.FAILED);
        assertThat(reservation.getFailureReason()).isEqualTo(ReservationFailureReason.PAYMENT_FAILED);
        assertThat(reservation.getUpdatedBy()).isZero();

        ArgumentCaptor<ReservationInbox> inboxCaptor = ArgumentCaptor.forClass(ReservationInbox.class);
        verify(reservationInboxRepositoryPort).save(inboxCaptor.capture());
        assertThat(inboxCaptor.getValue().getEventType()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void 이미_처리한_eventId이면_예매를_조회하거나_다시_처리하지_않는다() {
        // given
        PaymentSucceededCommand command = createPaymentSucceededCommand(EVENT_ID, AMOUNT);
        given(reservationInboxRepositoryPort.existsByEventId(EVENT_ID)).willReturn(true);

        // when
        boolean statusChanged = reservationPaymentEventService.processPaymentSucceeded(command);

        // then
        assertThat(statusChanged).isFalse();
        verifyNoInteractions(reservationRepositoryPort);
        verify(reservationInboxRepositoryPort, never()).save(any());
    }

    @Test
    void 확정된_예매에_실패_이벤트가_도착하면_상태는_유지하고_Inbox에는_기록한다() {
        // given
        Reservation reservation = createPaymentProcessingReservation();
        reservation.applyPaymentSucceeded(APPROVED_AT, 0L);
        PaymentFailedCommand command = createPaymentFailedCommand(EVENT_ID, AMOUNT);

        given(reservationInboxRepositoryPort.existsByEventId(EVENT_ID)).willReturn(false);
        given(reservationRepositoryPort.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));

        // when
        boolean statusChanged = reservationPaymentEventService.processPaymentFailed(command);

        // then
        assertThat(statusChanged).isFalse();
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getFailureReason()).isNull();
        verify(reservationInboxRepositoryPort).save(any(ReservationInbox.class));
    }

    @Test
    void 이벤트_결제금액이_예매금액과_다르면_상태와_Inbox를_변경하지_않는다() {
        // given
        Reservation reservation = createPaymentProcessingReservation();
        PaymentSucceededCommand command = createPaymentSucceededCommand(EVENT_ID, AMOUNT + 1_000L);

        given(reservationInboxRepositoryPort.existsByEventId(EVENT_ID)).willReturn(false);
        given(reservationRepositoryPort.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));

        // when
        BusinessException exception = catchThrowableOfType(
                () -> reservationPaymentEventService.processPaymentSucceeded(command),
                BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.PAYMENT_PROCESSING);
        verify(reservationInboxRepositoryPort, never()).save(any());
    }

    private Reservation createPaymentProcessingReservation() {
        Reservation reservation = Reservation.create(
                USER_ID,
                UUID.randomUUID(),
                EVENT_SESSION_ID,
                "RSV-260906-123456789ABC",
                "테스트 공연",
                Instant.parse("2026-09-07T10:00:00Z"),
                1,
                AMOUNT,
                "reservation-event-test",
                List.of()
        );
        ReflectionTestUtils.setField(reservation, "reservationId", RESERVATION_ID);
        reservation.markAsPaymentProcessing(PAYMENT_ID, USER_ID);
        return reservation;
    }

    private PaymentSucceededCommand createPaymentSucceededCommand(UUID eventId, Long amount) {
        return new PaymentSucceededCommand(eventId, PAYMENT_ID, RESERVATION_ID, USER_ID, amount, APPROVED_AT);
    }

    private PaymentFailedCommand createPaymentFailedCommand(UUID eventId, Long amount) {
        return new PaymentFailedCommand(eventId, PAYMENT_ID, RESERVATION_ID, USER_ID, amount);
    }
}
