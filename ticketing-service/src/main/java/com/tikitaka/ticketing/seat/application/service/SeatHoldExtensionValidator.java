package com.tikitaka.ticketing.seat.application.service;

import java.util.List;
import java.util.UUID;


public interface SeatHoldExtensionValidator {

    void validateAndExtend(UUID seatHoldId);

}
