package com.tikitaka.ticketing.seat.domain.entity;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.seat.domain.enums.HoldStatus;
import com.tikitaka.ticketing.seat.domain.enums.ReleaseReason;
import com.tikitaka.ticketing.seat.exception.SeatErrorCode;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "p_seat_hold")
public class SeatHold  {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "seat_hold_id", updatable = false, nullable = false)
    private UUID seatHoldId;

    @Column(name = "schedule_seat_id", nullable = false)
    private UUID scheduleSeatId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "hold_token", nullable = false, unique = true, updatable = false)
    private UUID holdToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "hold_status", nullable = false, length = 20)
    private HoldStatus holdStatus = HoldStatus.HOLDING;

    @Column(name = "held_at", nullable = false)
    private OffsetDateTime heldAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_reason", length = 30)
    private ReleaseReason releaseReason;


    private void validateStatusTransition(HoldStatus nextStatus) {
        if (!holdStatus.canTransitionTo(nextStatus)) {
            throw new BusinessException(
                    SeatErrorCode.INVALID_STATUS_TRANSITION
            );
        }
    }

}