package com.tikitaka.ticketing.reservation.domain.entity;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import com.tikitaka.ticketing.reservation.exception.ReservationErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    private static final Long USER_ID = 1L;

    @Test
    void 필수값으로_결제_대기_상태의_예매를_생성한다() {
        UUID eventId = UUID.randomUUID();
        UUID eventSessionId = UUID.randomUUID();
        Instant sessionStartAt = Instant.parse("2026-09-01T10:00:00Z");

        Reservation reservation = Reservation.create(
                USER_ID,
                eventId,
                eventSessionId,
                "R-20260901-0001",
                "테스트 공연",
                sessionStartAt,
                2,
                100_000L,
                "reservation-request-1",
                List.of()
        );

        assertThat(reservation.getUserId()).isEqualTo(USER_ID);
        assertThat(reservation.getEventId()).isEqualTo(eventId);
        assertThat(reservation.getEventSessionId()).isEqualTo(eventSessionId);
        assertThat(reservation.getReservationNumber()).isEqualTo("R-20260901-0001");
        assertThat(reservation.getEventTitle()).isEqualTo("테스트 공연");
        assertThat(reservation.getSessionStartAt()).isEqualTo(sessionStartAt);
        assertThat(reservation.getSeatCount()).isEqualTo(2);
        assertThat(reservation.getTotalAmount()).isEqualTo(100_000L);
        assertThat(reservation.getIdempotencyKey()).isEqualTo("reservation-request-1");
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.PAYMENT_PENDING);
        assertThat(reservation.getCreatedBy()).isEqualTo(USER_ID);
        assertThat(reservation.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(reservation.getPaymentId()).isNull();
        assertThat(reservation.getFailureReason()).isNull();
        assertThat(reservation.getPaymentCompletedAt()).isNull();
        assertThat(reservation.getReservationSeats()).isEmpty();
    }

    @Test
    void 좌석_수가_0이면_예매를_생성할_수_없다() {
        assertThatThrownBy(() -> Reservation.create(
                USER_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "R-20260901-0001",
                "테스트 공연",
                Instant.parse("2026-09-01T10:00:00Z"),
                0,
                0L,
                "reservation-request-1",
                List.of()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReservationErrorCode.INVALID_INPUT);
    }

    @Test
    void 총액이_음수이면_예매를_생성할_수_없다() {
        assertInvalidCreate("R-20260901-0001", 1, -1L, "reservation-request-1");
    }

    @Test
    void 예매_번호가_비어_있으면_예매를_생성할_수_없다() {
        assertInvalidCreate(" ", 1, 0L, "reservation-request-1");
    }

    @Test
    void 멱등성_키가_비어_있으면_예매를_생성할_수_없다() {
        assertInvalidCreate("R-20260901-0001", 1, 0L, " ");
    }

    @Test
    void 결제_대기부터_예매_확정까지_순서대로_상태를_변경한다() {
        Reservation reservation = new Reservation();

        reservation.updateStatus(ReservationStatus.PAYMENT_PROCESSING, USER_ID);
        reservation.updateStatus(ReservationStatus.CONFIRMED, USER_ID);

        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getUpdatedBy()).isEqualTo(USER_ID);
    }

    @Test
    void 예매_확정부터_취소_완료까지_순서대로_상태를_변경한다() {
        Reservation reservation = new Reservation();
        reservation.updateStatus(ReservationStatus.PAYMENT_PROCESSING, USER_ID);
        reservation.updateStatus(ReservationStatus.CONFIRMED, USER_ID);

        reservation.updateStatus(ReservationStatus.CANCEL_PENDING, USER_ID);
        reservation.updateStatus(ReservationStatus.CANCELLED, USER_ID);

        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void 결제_대기와_결제_처리_중에는_예매_실패로_변경할_수_있다() {
        Reservation pendingReservation = new Reservation();
        Reservation processingReservation = new Reservation();
        processingReservation.updateStatus(ReservationStatus.PAYMENT_PROCESSING, USER_ID);

        pendingReservation.updateStatus(ReservationStatus.FAILED, USER_ID);
        processingReservation.updateStatus(ReservationStatus.FAILED, USER_ID);

        assertThat(pendingReservation.getReservationStatus()).isEqualTo(ReservationStatus.FAILED);
        assertThat(processingReservation.getReservationStatus()).isEqualTo(ReservationStatus.FAILED);
    }

    @Test
    void 동일한_상태로의_변경은_아무_작업도_하지_않는다() {
        Reservation reservation = new Reservation();

        reservation.updateStatus(ReservationStatus.PAYMENT_PENDING, USER_ID);

        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.PAYMENT_PENDING);
        assertThat(reservation.getUpdatedBy()).isNull();
    }

    @Test
    void 허용되지_않은_상태로는_변경할_수_없다() {
        Reservation reservation = new Reservation();

        assertThatThrownBy(() -> reservation.updateStatus(ReservationStatus.CONFIRMED, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReservationErrorCode.INVALID_RESERVATION_STATUS_TRANSITION);
    }

    private void assertInvalidCreate(
            String reservationNumber,
            Integer seatCount,
            Long totalAmount,
            String idempotencyKey
    ) {
        assertThatThrownBy(() -> Reservation.create(
                USER_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                reservationNumber,
                "테스트 공연",
                Instant.parse("2026-09-01T10:00:00Z"),
                seatCount,
                totalAmount,
                idempotencyKey,
                List.of()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReservationErrorCode.INVALID_INPUT);
    }
}
