package com.tikitaka.ticketing.reservation.domain.port;

import com.tikitaka.ticketing.reservation.domain.model.SeatHoldValidationInfo;

import java.util.List;
import java.util.UUID;

public interface SeatHoldQueryPort {

    List<SeatHoldValidationInfo> findAllByIds(List<UUID> seatHoldIds);
}
