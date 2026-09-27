package com.tikitaka.ticketing.reservation.application.result;

import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import java.util.UUID;

public record ReservationPaymentEventResult(
        boolean statusChanged,
        UUID reservationId,
        UUID eventSessionId,
        Long userId
) {
    public static ReservationPaymentEventResult from(Reservation reservation, boolean statusChanged) {
        return new ReservationPaymentEventResult(
                statusChanged,
                reservation.getReservationId(),
                reservation.getEventSessionId(),
                reservation.getUserId()
        );
    }
}
