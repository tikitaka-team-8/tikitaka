package com.tikitaka.ticketing.seat.application.service;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import com.tikitaka.ticketing.seat.domain.repository.ScheduleSeatRepository;
import com.tikitaka.ticketing.seat.exception.SeatErrorCode;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatListResponse;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final ScheduleSeatRepository scheduleSeatRepository;

    public ScheduleSeatListResponse getSeatList(UUID eventSessionId, String section, String grade) {

        List<ScheduleSeat> seats =
                scheduleSeatRepository.findSeats(
                        eventSessionId,
                        section,
                        grade
                );
        return ScheduleSeatListResponse.from(seats);
    }

    public ScheduleSeatResponse getSeatDetail(
            UUID eventSessionId,
            UUID scheduleSeatId
    ) {
        ScheduleSeat seatDetail =
                scheduleSeatRepository
                        .findSeatDetail(
                                eventSessionId,
                                scheduleSeatId
                        )
                        .orElseThrow(() -> new BusinessException(
                                SeatErrorCode.SESSION_OR_SEAT_NOT_FOUND
                        ));
        return ScheduleSeatResponse.from(seatDetail);
    }

}
