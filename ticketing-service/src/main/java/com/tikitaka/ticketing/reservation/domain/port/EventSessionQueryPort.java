package com.tikitaka.ticketing.reservation.domain.port;

import com.tikitaka.ticketing.reservation.domain.model.ReservationEventSessionInfo;

import java.util.UUID;

public interface EventSessionQueryPort {

    ReservationEventSessionInfo getReservationInfo(UUID eventSessionId);
}
