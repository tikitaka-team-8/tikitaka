package com.tikitaka.ticketing.reservation.application.result;

import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class ReservationSearchResult {

    private final UUID reservationId;
    private final String reservationNumber;
    private final Long userId;
    private final UUID eventId;
    private final UUID eventSessionId;
    private final String eventTitle;
    private final Instant sessionStartAt;
    private final ReservationStatus reservationStatus;
    private final Integer seatCount;
    private final Long totalAmount;
    private final Instant createdAt;

    public ReservationSearchResult(Reservation reservation) {
        this.reservationId = reservation.getReservationId();
        this.reservationNumber = reservation.getReservationNumber();
        this.userId = reservation.getUserId();
        this.eventId = reservation.getEventId();
        this.eventSessionId = reservation.getEventSessionId();
        this.eventTitle = reservation.getEventTitle();
        this.sessionStartAt = reservation.getSessionStartAt();
        this.reservationStatus = reservation.getReservationStatus();
        this.seatCount = reservation.getSeatCount();
        this.totalAmount = reservation.getTotalAmount();
        this.createdAt = reservation.getCreatedAt();
    }
}
