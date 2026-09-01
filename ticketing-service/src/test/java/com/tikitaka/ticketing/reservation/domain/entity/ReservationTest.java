package com.tikitaka.ticketing.reservation.domain.entity;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import com.tikitaka.ticketing.reservation.exception.ReservationErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    private static final Long USER_ID = 1L;

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
}
