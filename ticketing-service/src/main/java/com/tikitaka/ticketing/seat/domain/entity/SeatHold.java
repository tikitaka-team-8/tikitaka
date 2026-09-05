package com.tikitaka.ticketing.seat.domain.entity;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.persistence.entity.BaseEntity;
import com.tikitaka.ticketing.seat.domain.enums.HoldStatus;
import com.tikitaka.ticketing.seat.domain.enums.ReleaseReason;
import com.tikitaka.ticketing.seat.exception.SeatErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "p_seat_hold")
public class SeatHold extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "seat_hold_id", updatable = false, nullable = false)
    private UUID seatHoldId;

    @Column(name = "schedule_seat_id", nullable = false, updatable = false)
    private UUID scheduleSeatId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "hold_token", nullable = false, unique = true, updatable = false)
    private UUID holdToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "hold_status", nullable = false, length = 20)
    private HoldStatus holdStatus = HoldStatus.HOLDING;

    @Column(name = "held_at", nullable = false, updatable = false)
    private Instant heldAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_reason", length = 30)
    private ReleaseReason releaseReason;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "extended_at")
    private Instant extendedAt;

    private SeatHold(Long userId) {
        super(userId);
    }

    public static SeatHold hold(
            Long userId,
            UUID scheduleSeatId,
            String idempotencyKey,
            Instant heldAt,
            Instant expiresAt
    ) {
        validateCoreInvariants(userId, scheduleSeatId, idempotencyKey, heldAt, expiresAt);

        SeatHold seatHold = new SeatHold(userId);
        seatHold.scheduleSeatId = scheduleSeatId;
        seatHold.userId = userId;
        seatHold.holdToken = UUID.randomUUID();
        seatHold.holdStatus = HoldStatus.HOLDING;
        seatHold.heldAt = heldAt;
        seatHold.expiresAt = expiresAt;
        seatHold.idempotencyKey = idempotencyKey;

        return seatHold;
    }

    private static void validateCoreInvariants(
            Long userId,
            UUID scheduleSeatId,
            String idempotencyKey,
            Instant heldAt,
            Instant expiresAt
    ) {
        if (userId == null
                || scheduleSeatId == null
                || idempotencyKey == null || idempotencyKey.isBlank()
                || heldAt == null || expiresAt == null
                || !heldAt.isBefore(expiresAt)) {
            throw new BusinessException(SeatErrorCode.INVALID_INPUT);
        }
    }

    public void release(ReleaseReason reason, Instant releasedAt) {
        validateStatusTransition(HoldStatus.RELEASED);
        this.holdStatus = HoldStatus.RELEASED;
        this.releasedAt = releasedAt;
        this.releaseReason = reason;
    }

    private void validateStatusTransition(HoldStatus nextStatus) {
        if (!holdStatus.canTransitionTo(nextStatus)) {
            throw new BusinessException(
                    SeatErrorCode.INVALID_STATUS_TRANSITION
            );
        }
    }

    public void extendExpiry(Instant now, Duration extension) {
        if (extendedAt != null) {
            return;
        }
        this.expiresAt = now.plus(extension);
        this.extendedAt = now;
    }
}
