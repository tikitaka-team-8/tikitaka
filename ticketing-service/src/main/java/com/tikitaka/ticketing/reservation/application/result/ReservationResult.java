package com.tikitaka.ticketing.reservation.application.result;

import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationFailureReason;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import com.tikitaka.ticketing.reservation.domain.model.ReservationSeatDetail;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
public class ReservationResult {

    private final UUID reservationId;
    private final String reservationNumber;
    private final Long userId;
    private final UUID eventId;
    private final UUID eventSessionId;
    private final String eventTitle;
    private final Instant sessionStartAt;
    private final ReservationStatus reservationStatus;
    private final ReservationFailureReason failureReason;
    private final Integer seatCount;
    private final Long totalAmount;
    private final Instant paymentCompletedAt;
    private final Instant createdAt;
    private final List<ReservationSeatResult> seats;

    public ReservationResult(Reservation reservation, List<ReservationSeatDetail> seatDetails) {
        this.reservationId = reservation.getReservationId();
        this.reservationNumber = reservation.getReservationNumber();
        this.userId = reservation.getUserId();
        this.eventId = reservation.getEventId();
        this.eventSessionId = reservation.getEventSessionId();
        this.eventTitle = reservation.getEventTitle();
        this.sessionStartAt = reservation.getSessionStartAt();
        this.reservationStatus = reservation.getReservationStatus();
        this.failureReason = reservation.getFailureReason();
        this.seatCount = reservation.getSeatCount();
        this.totalAmount = reservation.getTotalAmount();
        this.paymentCompletedAt = reservation.getPaymentCompletedAt();
        this.createdAt = reservation.getCreatedAt();
        this.seats = seatDetails.stream()
                .map(ReservationSeatResult::new)
                .toList();
    }
}
