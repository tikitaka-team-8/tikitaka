package com.tikitaka.ticketing.seat.domain.entity;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.persistence.entity.BaseEntity;
import com.tikitaka.ticketing.seat.domain.enums.SeatStatus;
import com.tikitaka.ticketing.seat.exception.SeatErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.*;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "p_schedule_seat")
public class ScheduleSeat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "schedule_seat_id", updatable = false, nullable = false)
    private UUID scheduleSeatId;

    @Column(name = "event_session_id", nullable = false, updatable = false)
    private UUID eventSessionId;

    @Column(name = "venue_seat_id", nullable = false, updatable = false)
    private UUID venueSeatId;

    @Column(name = "section", nullable = false, length = 20)
    private String section;

    @Column(name = "row_label", nullable = false, length = 20)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;

    @Column(name = "seat_grade", nullable = false, length = 30)
    private String seatGrade;

    @Column(name = "price", nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_status", nullable = false, length = 20)
    private SeatStatus seatStatus = SeatStatus.AVAILABLE;


//   좌석을 선점 상태(HELD)로 전이
    public void hold() {
        if (seatStatus == SeatStatus.EXCLUDED) {
            throw new BusinessException(SeatErrorCode.SEAT_NOT_FOR_SALE);
        }
        if (seatStatus != SeatStatus.AVAILABLE) {
            throw new BusinessException(SeatErrorCode.SEAT_UNAVAILABLE);
        }

        validateStatusTransition(SeatStatus.HELD);
        this.seatStatus = SeatStatus.HELD;
    }

//   선점 취소/만료 등으로 좌석을 다시 판매 가능(AVAILABLE) 상태로 변경
    public void release() {
        validateStatusTransition(SeatStatus.AVAILABLE);
        this.seatStatus = SeatStatus.AVAILABLE;
    }

    private void validateStatusTransition(SeatStatus nextStatus) {
        if (!seatStatus.canTransitionTo(nextStatus)) {
            throw new BusinessException(
                    SeatErrorCode.INVALID_STATUS_TRANSITION
            );
        }
    }

}
