package com.tikitaka.ticketing.reservation.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tikitaka.ticketing.reservation.application.result.ReservationResult;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationFailureReason;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
public class ReservationResDto {

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
    private final List<ReservationSeatResDto> seats;

    public ReservationResDto(ReservationResult result) {
        this.reservationId = result.getReservationId();
        this.reservationNumber = result.getReservationNumber();
        this.userId = result.getUserId();
        this.eventId = result.getEventId();
        this.eventSessionId = result.getEventSessionId();
        this.eventTitle = result.getEventTitle();
        this.sessionStartAt = result.getSessionStartAt();
        this.reservationStatus = result.getReservationStatus();
        this.failureReason = result.getFailureReason();
        this.seatCount = result.getSeatCount();
        this.totalAmount = result.getTotalAmount();
        this.paymentCompletedAt = result.getPaymentCompletedAt();
        this.createdAt = result.getCreatedAt();
        this.seats = result.getSeats().stream()
                .map(ReservationSeatResDto::new)
                .toList();
    }
}
