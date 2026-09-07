package com.tikitaka.ticketing.reservation.domain.entity;

import com.tikitaka.ticketing.reservation.domain.enums.ReservationOutboxEventType;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationOutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "p_reservation_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationOutbox {

    @Id
    @Column(updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false)
    private UUID reservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100, updatable = false)
    private ReservationOutboxEventType eventType;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationOutboxStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant publishedAt;

    private ReservationOutbox(UUID eventId, UUID reservationId, ReservationOutboxEventType eventType, String payload) {
        this.eventId = eventId;
        this.reservationId = reservationId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = ReservationOutboxStatus.PENDING;
    }

    public static ReservationOutbox create(UUID eventId, UUID reservationId, ReservationOutboxEventType eventType, String payload) {
        return new ReservationOutbox(eventId, reservationId, eventType, payload);
    }

    public void markAsPublished(Instant publishedAt) {
        this.status = ReservationOutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }
}
