package com.tikitaka.ticketing.seat.application.service;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.queue.application.QueueAdmissionValidator;
import com.tikitaka.ticketing.seat.application.command.CreateScheduleSeatsCommand;
import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import com.tikitaka.ticketing.seat.domain.entity.SeatHold;
import com.tikitaka.ticketing.seat.domain.enums.HoldStatus;
import com.tikitaka.ticketing.seat.domain.enums.ReleaseReason;
import com.tikitaka.ticketing.seat.domain.repository.ScheduleSeatRepository;
import com.tikitaka.ticketing.seat.domain.repository.SeatHoldRepository;
import com.tikitaka.ticketing.seat.exception.SeatErrorCode;
import com.tikitaka.ticketing.seat.presentation.dto.response.CreateScheduleSeatsResponse;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatService implements SeatHoldReservationValidator {

    private static final Duration HOLD_EXTENSION_DURATION = Duration.ofMinutes(10);
    private static final Long SYSTEM_USER_ID = 0L;

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


    @Transactional
    public void cancelHold(UUID seatHoldId, Long userId) {

        SeatHold seatHold = getSeatHoldOrThrow(seatHoldId);
        if (!Objects.equals(seatHold.getUserId(), userId)) {
            throw new BusinessException(SeatErrorCode.SEAT_HOLD_OWNERSHIP_REQUIRED);
        }
        if (seatHold.getHoldStatus() == HoldStatus.RELEASED) {
            return;
        }
        ScheduleSeat seat = getScheduleSeatForUpdateOrThrow(seatHold.getScheduleSeatId());

        if (seatHold.getHoldStatus() != HoldStatus.HOLDING) {
            throw new BusinessException(SeatErrorCode.SEAT_HOLD_ALREADY_CLOSED);
        }
        releaseIfHolding(seatHold, seat, ReleaseReason.USER_CANCEL);
    }

    @Override
    @Transactional
    public void validateAndExtend(UUID seatHoldId) {

        if (seatHoldId == null) {
            throw new BusinessException(SeatErrorCode.SEAT_HOLD_NOT_FOUND);
        }
        SeatHold seatHold = getSeatHoldOrThrow(seatHoldId);

        Instant now = Instant.now(clock);
        if (seatHold.getHoldStatus() != HoldStatus.HOLDING || seatHold.isExpired(now)) {
            throw new BusinessException(SeatErrorCode.SEAT_HOLD_ALREADY_CLOSED);
        }

        seatHold.reserve(now);
//      만료시간 10분 연장
//      seatHold.extendExpiry(now, HOLD_EXTENSION_DURATION);
    }
    public List<UUID> findOverdueHoldIds(int batchSize) {
        Instant now = Instant.now(clock);
        return seatHoldRepository.findExpiredHolds(now, batchSize).stream()
                .map(SeatHold::getSeatHoldId)
                .toList();
    }

    @Transactional
    public void expireHold(UUID seatHoldId) {
        Optional<SeatHold> maybeSeatHold = seatHoldRepository.findByIdForUpdate(seatHoldId);
        if (maybeSeatHold.isEmpty()) {
            return;
        }
        SeatHold seatHold = maybeSeatHold.get();
        if (seatHold.getHoldStatus() != HoldStatus.HOLDING) {
            return;
        }

        Instant now = Instant.now(clock);
        if (!seatHold.isExpired(now)) {
            return;
        }

        ScheduleSeat seat = getScheduleSeatForUpdateOrThrow(seatHold.getScheduleSeatId());
        releaseIfHolding(seatHold, seat, ReleaseReason.EXPIRED);
    }

    @Override
    @Transactional
    public void confirmHold(UUID seatHoldId) {
        SeatHold seatHold = getSeatHoldOrThrow(seatHoldId);
        if (seatHold.getHoldStatus() == HoldStatus.CONFIRMED) {
            return;
        }
        ScheduleSeat seat = getScheduleSeatForUpdateOrThrow(seatHold.getScheduleSeatId());

        // 결제 이벤트는 신뢰하고 처리한다 - 만료 시각을 다시 확인하지 않고,
        // 좌석 선점이 RESERVED 상태인지만 검증한다.
        if (seatHold.getHoldStatus() != HoldStatus.RESERVED) {
            throw new BusinessException(SeatErrorCode.SEAT_HOLD_ALREADY_CLOSED);
        }
        seatHold.confirm(Instant.now(clock));
        seat.sell();
    }

    @Override
    @Transactional
    public void releaseHold(UUID seatHoldId, ReleaseReason reason) {
        SeatHold seatHold = getSeatHoldOrThrow(seatHoldId);
        if (seatHold.getHoldStatus() == HoldStatus.RELEASED) {
            return;
        }
        ScheduleSeat seat = getScheduleSeatForUpdateOrThrow(seatHold.getScheduleSeatId());

        // 결제 실패/타임아웃은 HOLDING(결제 시작 전)과 RESERVED(결제 처리 중) 양쪽에서 다 일어날 수 있다.
        if (!seatHold.getHoldStatus().canTransitionTo(HoldStatus.RELEASED)) {
            throw new BusinessException(SeatErrorCode.SEAT_HOLD_ALREADY_CLOSED);
        }
        releaseIfHolding(seatHold, seat, reason);
    }


    @Transactional
    public CreateScheduleSeatsResponse createScheduleSeats(CreateScheduleSeatsCommand command) {
        if (command.seats() == null || command.seats().isEmpty()) {
            throw new BusinessException(SeatErrorCode.INVALID_INPUT);
        }

        List<UUID> venueSeatIds = command.seats().stream()
                .map(CreateScheduleSeatsCommand.SeatItem::venueSeatId)
                .toList();

        if (new HashSet<>(venueSeatIds).size() != venueSeatIds.size()) {
            throw new BusinessException(SeatErrorCode.INVALID_INPUT);
        }

        Set<UUID> existingVenueSeatIds = new HashSet<>(
                scheduleSeatRepository.findExistingVenueSeatIds(command.eventSessionId(), venueSeatIds)
        );

        List<ScheduleSeat> scheduleSeatsToCreate = new ArrayList<>();
        int skippedCount = 0;

        for (CreateScheduleSeatsCommand.SeatItem seatItem : command.seats()) {
            if (existingVenueSeatIds.contains(seatItem.venueSeatId())) {
                skippedCount++;
                continue;
            }
            scheduleSeatsToCreate.add(ScheduleSeat.create(
                    command.eventSessionId(),
                    seatItem.venueSeatId(),
                    seatItem.section(),
                    seatItem.rowLabel(),
                    seatItem.seatNumber(),
                    seatItem.grade(),
                    seatItem.price(),
                    SYSTEM_USER_ID
            ));
        }

        if (!scheduleSeatsToCreate.isEmpty()) {
            scheduleSeatRepository.saveAll(scheduleSeatsToCreate);
        }

        return CreateScheduleSeatsResponse.of(command.eventSessionId(), scheduleSeatsToCreate.size(), skippedCount);
    }


    @Transactional
    public List<CreateScheduleSeatsResponse> createScheduleSeatsBatch(List<CreateScheduleSeatsCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new BusinessException(SeatErrorCode.INVALID_INPUT);
        }
        return commands.stream()
                .map(this::createScheduleSeats)
                .toList();
    }

    private SeatHold getSeatHoldOrThrow(UUID seatHoldId) {
        return seatHoldRepository.findByIdForUpdate(seatHoldId)
                .orElseThrow(() -> new BusinessException(SeatErrorCode.SEAT_HOLD_NOT_FOUND));
    }

    private ScheduleSeat getScheduleSeatForUpdateOrThrow(UUID scheduleSeatId) {
        return scheduleSeatRepository.findByIdForUpdate(scheduleSeatId)
                .orElseThrow(() -> new BusinessException(SeatErrorCode.SESSION_OR_SEAT_NOT_FOUND));
    }

    private void releaseIfHolding(SeatHold seatHold, ScheduleSeat seat, ReleaseReason reason) {
        if (seatHold.getHoldStatus().canTransitionTo(HoldStatus.RELEASED)) {
            seatHold.release(reason, Instant.now(clock));
            seat.release();
        }
    }

}
