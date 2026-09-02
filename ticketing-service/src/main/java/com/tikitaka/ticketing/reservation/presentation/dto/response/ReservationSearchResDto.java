package com.tikitaka.ticketing.reservation.presentation.dto.response;

import com.tikitaka.ticketing.reservation.application.result.ReservationSearchResult;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class ReservationSearchResDto {

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

    public ReservationSearchResDto(ReservationSearchResult result) {
        this.reservationId = result.getReservationId();
        this.reservationNumber = result.getReservationNumber();
        this.userId = result.getUserId();
        this.eventId = result.getEventId();
        this.eventSessionId = result.getEventSessionId();
        this.eventTitle = result.getEventTitle();
        this.sessionStartAt = result.getSessionStartAt();
        this.reservationStatus = result.getReservationStatus();
        this.seatCount = result.getSeatCount();
        this.totalAmount = result.getTotalAmount();
        this.createdAt = result.getCreatedAt();
    }
}
