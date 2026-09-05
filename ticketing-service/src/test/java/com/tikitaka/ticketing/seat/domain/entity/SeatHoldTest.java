package com.tikitaka.ticketing.seat.domain.entity;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.seat.domain.enums.HoldStatus;
import com.tikitaka.ticketing.seat.exception.SeatErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatHoldTest {

    private final Long userId = 1L;
    private final UUID scheduleSeatId = UUID.randomUUID();
    private final String idempotencyKey = "idempotency-key";
    private final Instant heldAt = Instant.parse("2026-08-26T21:00:00+09:00");
    private final Instant expiresAt = Instant.parse("2026-08-26T21:05:00+09:00");

    @Test
    void 좌석_선점_이력을_생성한다() {

        SeatHold seatHold = SeatHold.hold(userId, scheduleSeatId, idempotencyKey, heldAt, expiresAt);

        assertThat(seatHold.getScheduleSeatId()).isEqualTo(scheduleSeatId);
        assertThat(seatHold.getUserId()).isEqualTo(userId);
        assertThat(seatHold.getHoldToken()).isNotNull();
        assertThat(seatHold.getHoldStatus()).isEqualTo(HoldStatus.HOLDING);
        assertThat(seatHold.getHeldAt()).isEqualTo(heldAt);
        assertThat(seatHold.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(seatHold.getIdempotencyKey()).isEqualTo(idempotencyKey);
    }

    @Test
    void 만료_시각이_선점_시각보다_늦지_않으면_예외가_발생한다() {

        assertThatThrownBy(() ->
                SeatHold.hold(userId, scheduleSeatId, idempotencyKey, expiresAt, heldAt)
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.INVALID_INPUT);
    }

    @Test
    void Idempotency_Key가_없으면_예외가_발생한다() {

        assertThatThrownBy(() ->
                SeatHold.hold(userId, scheduleSeatId, " ", heldAt, expiresAt)
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.INVALID_INPUT);
    }
}
