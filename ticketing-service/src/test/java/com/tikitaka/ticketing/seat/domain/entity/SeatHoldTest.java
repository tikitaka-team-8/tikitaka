package com.tikitaka.ticketing.seat.domain.entity;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.seat.domain.enums.HoldStatus;
import com.tikitaka.ticketing.seat.domain.enums.ReleaseReason;
import com.tikitaka.ticketing.seat.exception.SeatErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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

    @Test
    void HOLDING_상태의_선점을_취소하면_RELEASED_상태가_되고_취소_사유가_기록된다() {

        SeatHold seatHold = SeatHold.hold(userId, scheduleSeatId, idempotencyKey, heldAt, expiresAt);
        Instant releasedAt = expiresAt.minusSeconds(60);

        seatHold.release(ReleaseReason.USER_CANCEL, releasedAt);

        assertThat(seatHold.getHoldStatus()).isEqualTo(HoldStatus.RELEASED);
        assertThat(seatHold.getReleasedAt()).isEqualTo(releasedAt);
        assertThat(seatHold.getReleaseReason()).isEqualTo(ReleaseReason.USER_CANCEL);
    }

    @Test
    void 이미_RELEASED된_선점을_다시_취소하면_예외가_발생한다() {

        SeatHold seatHold = SeatHold.hold(userId, scheduleSeatId, idempotencyKey, heldAt, expiresAt);
        seatHold.release(ReleaseReason.USER_CANCEL, expiresAt.minusSeconds(60));

        assertThatThrownBy(() -> seatHold.release(ReleaseReason.USER_CANCEL, expiresAt))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void CONFIRMED_상태의_선점을_취소하려하면_예외가_발생한다() {

        SeatHold seatHold = SeatHold.hold(userId, scheduleSeatId, idempotencyKey, heldAt, expiresAt);
        org.springframework.test.util.ReflectionTestUtils.setField(seatHold, "holdStatus", HoldStatus.CONFIRMED);

        assertThatThrownBy(() -> seatHold.release(ReleaseReason.USER_CANCEL, expiresAt))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void HOLDING_상태의_선점은_만료_시각을_연장할_수_있다() {

        SeatHold seatHold = SeatHold.hold(userId, scheduleSeatId, idempotencyKey, heldAt, expiresAt);
        Instant extendAt = expiresAt.minusSeconds(30);
        Duration extension = Duration.ofMinutes(10);

        seatHold.extendExpiry(extendAt, extension);

        assertThat(seatHold.getExpiresAt()).isEqualTo(extendAt.plus(extension));
        assertThat(seatHold.getExtendedAt()).isEqualTo(extendAt);
        assertThat(seatHold.getHoldStatus()).isEqualTo(HoldStatus.HOLDING);
    }

    @Test
    void 이미_연장된_선점은_다시_연장을_요청해도_그대로_유지된다() {

        SeatHold seatHold = SeatHold.hold(userId, scheduleSeatId, idempotencyKey, heldAt, expiresAt);
        Instant firstExtendAt = expiresAt.minusSeconds(30);
        Duration extension = Duration.ofMinutes(10);
        seatHold.extendExpiry(firstExtendAt, extension);
        Instant expiresAfterFirstExtension = seatHold.getExpiresAt();

        seatHold.extendExpiry(firstExtendAt.plusSeconds(60), extension);

        assertThat(seatHold.getExpiresAt()).isEqualTo(expiresAfterFirstExtension);
        assertThat(seatHold.getExtendedAt()).isEqualTo(firstExtendAt);
    }
}
