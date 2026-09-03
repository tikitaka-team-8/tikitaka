package com.tikitaka.ticketing.seat.application.service;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.queue.application.QueueService;
import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import com.tikitaka.ticketing.seat.domain.repository.ScheduleSeatRepository;
import com.tikitaka.ticketing.seat.domain.repository.SeatHoldRepository;
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
    private final SeatHoldRepository seatHoldRepository;
    private final QueueService queueService;



    public ScheduleSeatListResponse getSeatList(UUID eventSessionId, String section, String grade, Long userId, String admissionToken) {

        queueService.validateAndEnter(eventSessionId,userId,admissionToken);

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
        queueService.validateEntered(eventSessionId,userId);
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

    /**
     * - Idempotency-Key로 재요청 여부를 먼저 확인해 동일 응답을 반환
     * - 대기열 검증 -> queueService.validateEntered ENTERED 인지만 검증
     * - 대상 schedule_seat row에 비관적 락을 건 뒤 상태를 검증/전이하고 선점 이력을 남긴다.
     */
//    @Transactional
//    public SeatHoldResponse holdSeat(
//            UUID eventSessionId,
//            UUID scheduleSeatId,
//            Long userId,
//            String idempotencyKey
//    ) {
//        queueService.validateEntered(eventSessionId, userId);
//
//
//        Optional<SeatHold> existingHold =
//                seatHoldRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
//        if (existingHold.isPresent()) {
//            return SeatHoldResponse.from(existingHold.get());
//        }
//
//        ScheduleSeat seat = scheduleSeatRepository
//                .findByIdForUpdate(eventSessionId, scheduleSeatId)
//                .orElseThrow(() -> new BusinessException(SeatErrorCode.SESSION_OR_SEAT_NOT_FOUND));
//
//        seat.hold();
//
//        OffsetDateTime heldAt = Instant.now(clock).atZone(SEOUL_ZONE_ID).toOffsetDateTime();
//        OffsetDateTime expiresAt = heldAt.plus(seatHoldProperties.duration());
//
//        SeatHold seatHold = SeatHold.hold(userId, seat.getScheduleSeatId(), idempotencyKey, heldAt, expiresAt);
//
//        SeatHold savedHold;
//        try {
//            savedHold = seatHoldRepository.save(seatHold);
//        } catch (DataIntegrityViolationException exception) {
//            // 동시에 들어온 동일 Idempotency-Key 재요청과 경합한 경우, 먼저 커밋된 결과를 그대로 반환한다.
//            savedHold = seatHoldRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
//                    .orElseThrow(() -> exception);
//        }
//
//        return SeatHoldResponse.from(savedHold);
//    }



}
