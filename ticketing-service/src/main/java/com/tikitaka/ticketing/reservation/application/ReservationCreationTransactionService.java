package com.tikitaka.ticketing.reservation.application;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.exception.CommonErrorCode;
import com.tikitaka.ticketing.reservation.application.command.CreateReservationCommand;
import com.tikitaka.ticketing.reservation.application.result.CreateReservationResult;
import com.tikitaka.ticketing.reservation.application.result.ReservationCreationPreparation;
import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.entity.ReservationSeat;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import com.tikitaka.ticketing.reservation.domain.model.PaymentCreationInfo;
import com.tikitaka.ticketing.reservation.domain.model.ReservationCreationSeatInfo;
import com.tikitaka.ticketing.reservation.domain.model.ReservationEventSessionInfo;
import com.tikitaka.ticketing.reservation.domain.model.ReservationSeatCreationData;
import com.tikitaka.ticketing.reservation.domain.port.EventSessionQueryPort;
import com.tikitaka.ticketing.reservation.domain.port.ReservationRepositoryPort;
import com.tikitaka.ticketing.reservation.domain.port.SeatHoldQueryPort;
import com.tikitaka.ticketing.reservation.exception.ReservationErrorCode;
import com.tikitaka.ticketing.seat.application.service.SeatHoldReservationValidator;
import com.tikitaka.ticketing.seat.domain.enums.HoldStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReservationCreationTransactionService {
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter RESERVATION_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    private final ReservationRepositoryPort reservationRepositoryPort;
    private final SeatHoldQueryPort seatHoldQueryPort;
    private final EventSessionQueryPort eventSessionQueryPort;
    private final SeatHoldReservationValidator seatHoldReservationValidator;

    public ReservationCreationTransactionService(ReservationRepositoryPort reservationRepositoryPort,
            SeatHoldQueryPort seatHoldQueryPort, EventSessionQueryPort eventSessionQueryPort,
            SeatHoldReservationValidator seatHoldReservationValidator) {

        this.reservationRepositoryPort = reservationRepositoryPort;
        this.seatHoldQueryPort = seatHoldQueryPort;
        this.eventSessionQueryPort = eventSessionQueryPort;
        this.seatHoldReservationValidator = seatHoldReservationValidator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationCreationPreparation prepareReservation(CreateReservationCommand command) {

        // 요청 좌석과 기존 멱등 요청을 먼저 검증해 신규 생성 여부를 결정
        validateRequestedSeatHoldIds(command.getSeatHoldIds());
        Reservation existingReservation = reservationRepositoryPort
                .findByUserIdAndIdempotencyKey(command.getLoginUserId(), command.getIdempotencyKey())
                .orElse(null);

        if (existingReservation != null) {
            validateIdempotentRequest(existingReservation, command.getSeatHoldIds());
            return toPreparation(existingReservation, false);
        }

        // 신규 예매에 필요한 좌석·회차 스냅샷을 검증하고 예매 의도를 저장
        List<ReservationCreationSeatInfo> seatInfos =
                findValidatedCreationSeats(command.getLoginUserId(), command.getSeatHoldIds());
        UUID eventSessionId = resolveEventSessionId(seatInfos);
        int seatCount = seatInfos.size();
        long totalAmount = calculateTotalAmount(seatInfos);

        List<ReservationSeatCreationData> reservationSeatCreationData = seatInfos.stream()
                .map(seatInfo -> new ReservationSeatCreationData(
                        seatInfo.seatHoldId(), seatInfo.scheduleSeatId(), seatInfo.price()))
                .toList();

        ReservationEventSessionInfo eventSessionInfo = eventSessionQueryPort.getReservationInfo(eventSessionId);
        validateEventSessionInfo(eventSessionId, eventSessionInfo);

        Reservation reservation = Reservation.create(
                command.getLoginUserId(), eventSessionInfo.eventId(), eventSessionId, generateReservationNumber(),
                eventSessionInfo.eventTitle(), eventSessionInfo.sessionStartAt().toInstant(), seatCount, totalAmount,
                command.getIdempotencyKey(), reservationSeatCreationData
        );
        Reservation savedReservation = reservationRepositoryPort.save(reservation);

        // Payment 생성 전에 좌석을 RESERVED로 전환하고 이 트랜잭션에서 함께 커밋
        savedReservation.getReservationSeats().forEach(
                reservationSeat -> seatHoldReservationValidator.validateAndExtend(reservationSeat.getSeatHoldId())
        );

        return toPreparation(savedReservation, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreateReservationResult completePaymentCreation(ReservationCreationPreparation preparation, PaymentCreationInfo paymentCreationInfo, Long loginUserId) {

        // 최신 예매를 다시 조회해 Payment 응답과 연결 대상을 검증
        Reservation reservation = reservationRepositoryPort.findById(preparation.reservationId())
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        validatePaymentCreationInfo(reservation, paymentCreationInfo);

        // 결제 ID를 연결하고 PAYMENT_PROCESSING 전환만 별도 트랜잭션으로 커밋
        reservation.markAsPaymentProcessing(paymentCreationInfo.paymentId(), loginUserId);

        return new CreateReservationResult(reservation, preparation.created());
    }

    private ReservationCreationPreparation toPreparation(Reservation reservation, boolean created) {
        return new ReservationCreationPreparation(
                reservation.getReservationId(), reservation.getUserId(), reservation.getTotalAmount(),
                reservation.getIdempotencyKey(), created,
                reservation.getReservationStatus() == ReservationStatus.PAYMENT_PENDING,
                new CreateReservationResult(reservation, created)
        );
    }

    private void validateRequestedSeatHoldIds(List<UUID> seatHoldIds) {
        if (seatHoldIds == null || seatHoldIds.isEmpty()
                || seatHoldIds.stream().anyMatch(Objects::isNull)
                || seatHoldIds.stream().distinct().count() != seatHoldIds.size()) {
            throw new BusinessException(ReservationErrorCode.INVALID_INPUT);
        }
    }

    private List<ReservationCreationSeatInfo> findValidatedCreationSeats(Long loginUserId, List<UUID> seatHoldIds) {
        List<ReservationCreationSeatInfo> seatInfos = seatHoldQueryPort.findCreationInfosBySeatHoldIds(seatHoldIds);
        validateCreationSeats(loginUserId, seatHoldIds, seatInfos);
        return seatInfos;
    }

    private void validateIdempotentRequest(Reservation existingReservation, List<UUID> seatHoldIds) {
        Set<UUID> existingSeatHoldIds = existingReservation.getReservationSeats().stream()
                .map(ReservationSeat::getSeatHoldId)
                .collect(Collectors.toSet());

        if (existingSeatHoldIds.size() != seatHoldIds.size() || !existingSeatHoldIds.containsAll(seatHoldIds)) {
            throw new BusinessException(ReservationErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
    }

    private void validateEventSessionInfo(UUID eventSessionId, ReservationEventSessionInfo eventSessionInfo) {
        if (eventSessionInfo == null
                || !Objects.equals(eventSessionId, eventSessionInfo.eventSessionId())
                || eventSessionInfo.eventId() == null
                || eventSessionInfo.eventTitle() == null
                || eventSessionInfo.eventTitle().isBlank()
                || eventSessionInfo.sessionStartAt() == null) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_SERVICE_FAILURE);
        }
    }

    private void validatePaymentCreationInfo(Reservation reservation, PaymentCreationInfo paymentCreationInfo) {
        if (paymentCreationInfo == null
                || paymentCreationInfo.paymentId() == null
                || !Objects.equals(reservation.getReservationId(), paymentCreationInfo.reservationId())
                || !Objects.equals(reservation.getTotalAmount(), paymentCreationInfo.amount())
                || !"READY".equals(paymentCreationInfo.status())) {
            throw new BusinessException(ReservationErrorCode.PAYMENT_CREATION_FAILED);
        }
    }

    private void validateCreationSeats(Long loginUserId, List<UUID> seatHoldIds,
            List<ReservationCreationSeatInfo> seatInfos) {
        Set<UUID> foundSeatHoldIds = seatInfos.stream()
                .map(ReservationCreationSeatInfo::seatHoldId)
                .collect(Collectors.toSet());

        if (foundSeatHoldIds.size() != seatHoldIds.size() || !foundSeatHoldIds.containsAll(seatHoldIds)) {
            throw new BusinessException(ReservationErrorCode.SEAT_HOLD_NOT_FOUND);
        }
        if (seatInfos.stream().anyMatch(seatInfo -> !Objects.equals(seatInfo.userId(), loginUserId))) {
            throw new BusinessException(ReservationErrorCode.SEAT_HOLD_OWNERSHIP_REQUIRED);
        }
        if (seatInfos.stream().anyMatch(seatInfo -> seatInfo.holdStatus() != HoldStatus.HOLDING)) {
            throw new BusinessException(ReservationErrorCode.INVALID_SEAT_HOLD_STATUS);
        }

        Instant now = Instant.now();
        if (seatInfos.stream().anyMatch(seatInfo -> !seatInfo.expiresAt().isAfter(now))) {
            throw new BusinessException(ReservationErrorCode.SEAT_HOLD_EXPIRED);
        }

        validateSingleEventSession(seatInfos);
        if (!reservationRepositoryPort.findUsedSeatHoldIds(seatHoldIds).isEmpty()) {
            throw new BusinessException(ReservationErrorCode.RESERVATION_ALREADY_EXISTS);
        }
    }

    private void validateSingleEventSession(List<ReservationCreationSeatInfo> seatInfos) {
        Set<UUID> eventSessionIds = seatInfos.stream()
                .map(ReservationCreationSeatInfo::eventSessionId)
                .collect(Collectors.toSet());

        if (eventSessionIds.size() != 1 || eventSessionIds.contains(null)) {
            throw new BusinessException(ReservationErrorCode.INVALID_INPUT);
        }
    }

    private UUID resolveEventSessionId(List<ReservationCreationSeatInfo> seatInfos) {
        return seatInfos.get(0).eventSessionId();
    }

    private long calculateTotalAmount(List<ReservationCreationSeatInfo> seatInfos) {
        return seatInfos.stream().mapToLong(ReservationCreationSeatInfo::price).sum();
    }

    private String generateReservationNumber() {
        String date = LocalDate.now(SEOUL_ZONE_ID).format(RESERVATION_DATE_FORMATTER);
        String randomPart = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);

        return "RSV-" + date + "-" + randomPart;
    }
}
