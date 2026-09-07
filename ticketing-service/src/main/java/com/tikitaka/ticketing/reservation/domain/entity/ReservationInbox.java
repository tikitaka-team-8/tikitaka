package com.tikitaka.ticketing.reservation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "p_reservation_inbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationInbox {

    @Id
    @Column(updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false)
    private UUID reservationId;

    @Column(nullable = false, length = 100, updatable = false)
    private String eventType;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant processedAt;

    private ReservationInbox(UUID eventId, UUID reservationId, String eventType) {
        this.eventId = eventId;
        this.reservationId = reservationId;
        this.eventType = eventType;
    }

    public static ReservationInbox create(UUID eventId, UUID reservationId, String eventType) {
        return new ReservationInbox(eventId, reservationId, eventType);
    }
}
