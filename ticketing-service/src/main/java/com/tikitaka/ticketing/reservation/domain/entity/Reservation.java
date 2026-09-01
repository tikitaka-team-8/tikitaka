package com.tikitaka.ticketing.reservation.domain.entity;

import com.tikitaka.ticketing.global.persistence.entity.BaseEntity;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationFailureReason;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
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

}
