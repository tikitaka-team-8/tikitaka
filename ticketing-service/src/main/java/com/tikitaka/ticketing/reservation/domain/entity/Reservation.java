package com.tikitaka.ticketing.reservation.domain.entity;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.persistence.entity.BaseEntity;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationFailureReason;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import com.tikitaka.ticketing.reservation.exception.ReservationErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "p_reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Reservation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID reservationId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private UUID eventId; // 공연 아이디

    @Column(nullable = false, updatable = false)
    private UUID eventSessionId;

    private UUID paymentId;

    @Column(nullable = false, length = 30, updatable = false)
    private String reservationNumber;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<ReservationSeat> reservationSeats = new ArrayList<>();

    @Column(nullable = false, length = 200, updatable = false)
    private String eventTitle;

    @Column(nullable = false, updatable = false)
    private Instant sessionStartAt;

    @Column(nullable = false, updatable = false)
    private Integer seatCount; // reservationSeats의 원소 개수와 일치

    @Column(nullable = false, updatable = false)
    private Long totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus reservationStatus = ReservationStatus.PAYMENT_PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ReservationFailureReason failureReason;

    private Instant paymentCompletedAt;

    @Column(nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private boolean isDeleted = false;

    private Reservation(Long userId) {
        super(userId);
    }

    public static Reservation create(Long userId, UUID eventId, UUID eventSessionId, String reservationNumber, String eventTitle,
            Instant sessionStartAt, Integer seatCount, Long totalAmount, String idempotencyKey, List<ReservationSeat> reservationSeats) {

        validateCoreInvariants(reservationNumber, seatCount, totalAmount, idempotencyKey);

        Reservation reservation = new Reservation(userId);
        reservation.userId = userId;
        reservation.eventId = eventId;
        reservation.eventSessionId = eventSessionId;
        reservation.reservationNumber = reservationNumber;
        reservation.eventTitle = eventTitle;
        reservation.sessionStartAt = sessionStartAt;
        reservation.seatCount = seatCount;
        reservation.totalAmount = totalAmount;
        reservation.reservationStatus = ReservationStatus.PAYMENT_PENDING;
        reservation.idempotencyKey = idempotencyKey;
        reservation.addReservationSeats(reservationSeats);

        return reservation;
    }

    private static void validateCoreInvariants(String reservationNumber, Integer seatCount, Long totalAmount, String idempotencyKey) {

        if (reservationNumber == null || reservationNumber.isBlank() || seatCount == null || seatCount <= 0 || totalAmount == null
                || totalAmount < 0 || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ReservationErrorCode.INVALID_INPUT);
        }
    }

    public void addReservationSeats(List<ReservationSeat> reservationSeats) {
        // TODO: 예매 생성 로직 구현 시 예매-예매좌석 관련된 필드 채우는 내용 작성 예정
    }

    public void updateStatus(ReservationStatus nextStatus, Long userId) {
        if (reservationStatus == nextStatus) {
            return;
        }
        if (!canTransitTo(nextStatus)) {
            throw new BusinessException(ReservationErrorCode.INVALID_RESERVATION_STATUS_TRANSITION);
        }

        reservationStatus = nextStatus;
        markAsUpdated(userId);
    }

    @Override
    public void markAsDeleted(Long deletedBy, Instant deletedAt) {
        super.markAsDeleted(deletedBy, deletedAt);
        this.isDeleted = true;
        reservationSeats.forEach(
                reservationSeat -> reservationSeat.markAsDeleted(deletedBy, deletedAt)
        );
    }

    private boolean canTransitTo(ReservationStatus nextStatus) {
        return switch (reservationStatus) {
            case PAYMENT_PENDING -> nextStatus == ReservationStatus.PAYMENT_PROCESSING
                    || nextStatus == ReservationStatus.FAILED;
            case PAYMENT_PROCESSING -> nextStatus == ReservationStatus.CONFIRMED
                    || nextStatus == ReservationStatus.FAILED;
            case CONFIRMED -> nextStatus == ReservationStatus.CANCEL_PENDING;
            case CANCEL_PENDING -> nextStatus == ReservationStatus.CANCELLED;
            case FAILED, CANCELLED -> false;
        };
    }

}
