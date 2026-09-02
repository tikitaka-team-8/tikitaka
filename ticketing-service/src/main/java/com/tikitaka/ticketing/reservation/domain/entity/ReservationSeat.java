package com.tikitaka.ticketing.reservation.domain.entity;

import com.tikitaka.ticketing.global.persistence.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "p_reservation_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class ReservationSeat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID reservationSeatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private Reservation reservation;

    @Column(nullable = false, updatable = false)
    private UUID seatHoldId;

    @Column(nullable = false, updatable = false)
    private UUID scheduleSeatId;

    @Column(nullable = false, updatable = false)
    private Long price;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @Override
    protected void markAsDeleted(Long deletedBy, Instant deletedAt) {
        super.markAsDeleted(deletedBy, deletedAt);
        this.isDeleted = true;
    }


}
