package com.tikitaka.ticketing.seat.application.service;

import java.util.UUID;


public interface SeatHoldExtensionValidator {

    void validateAndExtend(UUID seatHoldId);

}
