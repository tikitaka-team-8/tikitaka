package com.tikitaka.ticketing.seat.domain.entity;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.seat.domain.enums.SeatStatus;
import com.tikitaka.ticketing.seat.exception.SeatErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleSeatTest {

    @Test
    void AVAILABLE_좌석은_선점하면_HELD_상태가_된다() {

        ScheduleSeat seat = new ScheduleSeat();
        ReflectionTestUtils.setField(seat, "seatStatus", SeatStatus.AVAILABLE);

        seat.hold();

        assertThat(seat.getSeatStatus()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void 이미_선점된_좌석을_선점하면_SEAT_UNAVAILABLE_예외가_발생한다() {

        ScheduleSeat seat = new ScheduleSeat();
        ReflectionTestUtils.setField(seat, "seatStatus", SeatStatus.HELD);

        assertThatThrownBy(seat::hold)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.SEAT_UNAVAILABLE);
    }

    @Test
    void 판매_완료된_좌석을_선점하면_SEAT_UNAVAILABLE_예외가_발생한다() {

        ScheduleSeat seat = new ScheduleSeat();
        ReflectionTestUtils.setField(seat, "seatStatus", SeatStatus.SOLD);

        assertThatThrownBy(seat::hold)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.SEAT_UNAVAILABLE);
    }

    @Test
    void 판매_제외된_좌석을_선점하면_SEAT_NOT_FOR_SALE_예외가_발생한다() {

        ScheduleSeat seat = new ScheduleSeat();
        ReflectionTestUtils.setField(seat, "seatStatus", SeatStatus.EXCLUDED);

        assertThatThrownBy(seat::hold)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.SEAT_NOT_FOR_SALE);
    }
}
