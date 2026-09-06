package com.tikitaka.ticketing.seat.application.service;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.queue.application.QueueAdmissionValidator;
import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import com.tikitaka.ticketing.seat.domain.entity.SeatHold;
import com.tikitaka.ticketing.seat.domain.repository.ScheduleSeatRepository;
import com.tikitaka.ticketing.seat.domain.repository.SeatHoldRepository;
import com.tikitaka.ticketing.seat.exception.SeatErrorCode;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatListResponse;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatResponse;
import com.tikitaka.ticketing.seat.presentation.dto.response.SeatHoldResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final ScheduleSeatRepository scheduleSeatRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final QueueAdmissionValidator queueAdmissionValidator;
    private final Clock clock;


    public ScheduleSeatListResponse getSeatList(UUID eventSessionId, String section, String grade, Long userId, String admissionToken) {

        queueAdmissionValidator.validateAndEnter(eventSessionId,userId,admissionToken);

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
            UUID scheduleSeatId,
            Long userId
    ) {
        queueAdmissionValidator.validateEntered(eventSessionId,userId);
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


    @Transactional
    public SeatHoldResponse holdSeat(
            UUID eventSessionId,
            UUID scheduleSeatId,
            Long userId,
            String idempotencyKey
    ) {
        queueAdmissionValidator.validateEntered(eventSessionId, userId);

        Optional<SeatHold> existingHold =
                seatHoldRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existingHold.isPresent()) {
            return SeatHoldResponse.from(existingHold.get());
        }

        ScheduleSeat seat = scheduleSeatRepository
                .findByIdForUpdate(eventSessionId, scheduleSeatId)
                .orElseThrow(() -> new BusinessException(SeatErrorCode.SESSION_OR_SEAT_NOT_FOUND));
        seat.hold();

        Instant heldAt = Instant.now(clock);
        Instant expiresAt = heldAt.plus(Duration.ofMinutes(10));
        SeatHold seatHold = SeatHold.hold(userId, seat.getScheduleSeatId(), idempotencyKey, heldAt, expiresAt);
        SeatHold savedHold;

        try {
            savedHold = seatHoldRepository.save(seatHold);
        } catch (DataIntegrityViolationException exception) {
            savedHold = seatHoldRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> exception);
        }

        return SeatHoldResponse.from(savedHold);
    }



}
