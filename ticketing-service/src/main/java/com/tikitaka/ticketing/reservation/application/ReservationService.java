package com.tikitaka.ticketing.reservation.application;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.exception.CommonErrorCode;
import com.tikitaka.ticketing.reservation.application.command.CreateReservationCommand;
import com.tikitaka.ticketing.reservation.application.command.GetReservationCommand;
import com.tikitaka.ticketing.reservation.application.command.PaymentValidationCommand;
import com.tikitaka.ticketing.reservation.application.command.SearchReservationsCommand;
import com.tikitaka.ticketing.reservation.application.result.CreateReservationResult;
import com.tikitaka.ticketing.reservation.application.result.PaymentValidationResult;
import com.tikitaka.ticketing.reservation.application.result.ReservationResult;
import com.tikitaka.ticketing.reservation.application.result.ReservationSearchResult;
import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.entity.ReservationSeat;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import com.tikitaka.ticketing.reservation.domain.model.ReservationCreationSeatInfo;
import com.tikitaka.ticketing.reservation.domain.model.ReservationEventSessionInfo;
import com.tikitaka.ticketing.reservation.domain.model.ReservationSeatCreationData;
import com.tikitaka.ticketing.reservation.domain.model.ReservationSeatInfo;
import com.tikitaka.ticketing.reservation.domain.model.SeatHoldValidationInfo;
import com.tikitaka.ticketing.reservation.domain.port.EventSessionQueryPort;
import com.tikitaka.ticketing.reservation.domain.port.ReservationRepositoryPort;
import com.tikitaka.ticketing.reservation.domain.port.SeatHoldQueryPort;
import com.tikitaka.ticketing.reservation.exception.ReservationErrorCode;
import com.tikitaka.ticketing.seat.domain.enums.HoldStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
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
@Transactional(readOnly = true)
public class ReservationService {
    private static final String USER_ROLE = "USER";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "sessionStartAt");
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter RESERVATION_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    private final ReservationRepositoryPort reservationRepositoryPort;
    private final SeatHoldQueryPort seatHoldQueryPort;
    private final EventSessionQueryPort eventSessionQueryPort;

    public ReservationService(ReservationRepositoryPort reservationRepositoryPort, SeatHoldQueryPort seatHoldQueryPort,
            EventSessionQueryPort eventSessionQueryPort) {
        this.reservationRepositoryPort = reservationRepositoryPort;
        this.seatHoldQueryPort = seatHoldQueryPort;
        this.eventSessionQueryPort = eventSessionQueryPort;
    }

    public ReservationResult getReservation(GetReservationCommand command) {

        Reservation reservation = reservationRepositoryPort.findById(command.getReservationId())
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        validateReadAuthority(command, reservation);

        List<ReservationSeatInfo> seatDetails =
                reservationRepositoryPort.findSeatDetailsByReservationId(reservation.getReservationId());

        return new ReservationResult(reservation, seatDetails);
    }

    public Page<ReservationSearchResult> searchReservations(SearchReservationsCommand command) {

        // 입력 값 검증
        Long ownerUserId = resolveOwnerUserId(command);
        String eventTitle = normalizeEventTitle(command.getEventTitle());
        ReservationStatus reservationStatus = resolveReservationStatus(command.getReservationStatus());
        Pageable pageable = normalizePageable(command.getPageable());

        return reservationRepositoryPort.searchReservations(ownerUserId, eventTitle, reservationStatus, pageable)
                .map(ReservationSearchResult::new);
    }


    public PaymentValidationResult validatePayment(PaymentValidationCommand command) {

        // 예매 조회
        Reservation reservation = reservationRepositoryPort.findById(command.getReservationId())
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        // 권한, 상태 검증
        validatePaymentAuthority(command, reservation);
        reservation.validatePaymentAvailability();

        List<UUID> seatHoldIds = reservation.getReservationSeats().stream()
                .map(reservationSeat -> reservationSeat.getSeatHoldId()).toList();

        if (seatHoldIds.isEmpty()) {
            throw new BusinessException(ReservationErrorCode.SEAT_HOLD_NOT_FOUND);
        }

        List<SeatHoldValidationInfo> seatHolds = seatHoldQueryPort.findAllByIds(seatHoldIds);
        validateSeatHolds(command.getUserId(), seatHoldIds, seatHolds);

        return new PaymentValidationResult(reservation);
    }

    @Transactional
    public CreateReservationResult createReservation(CreateReservationCommand command) {

        // 요청 좌석 선점 ID 형식 검증
        validateRequestedSeatHoldIds(command.getSeatHoldIds());

        // 동일 멱등 요청이면 기존 예매 반환
        Reservation existingReservation = reservationRepositoryPort
                .findByUserIdAndIdempotencyKey(command.getLoginUserId(), command.getIdempotencyKey())
                .orElse(null);
        if (existingReservation != null) {
            validateIdempotentRequest(existingReservation, command.getSeatHoldIds());
            return new CreateReservationResult(existingReservation, false);
        }

        // 예매 생성용 좌석 정보 조회 및 검증
        List<ReservationCreationSeatInfo> seatInfos =
                findValidatedCreationSeats(command.getLoginUserId(), command.getSeatHoldIds());

        // 예매 생성에 사용할 공연 회차, 좌석 수, 총금액 확정
        UUID eventSessionId = resolveEventSessionId(seatInfos);
        int seatCount = seatInfos.size();
        long totalAmount = calculateTotalAmount(seatInfos);

        // Reservation이 자식 엔티티를 생성할 수 있도록 좌석별 생성 데이터 구성
        List<ReservationSeatCreationData> reservationSeatCreationData =
                seatInfos.stream().map(
                        seatInfo -> new ReservationSeatCreationData(
                        seatInfo.seatHoldId(), seatInfo.scheduleSeatId(), seatInfo.price()
                )).toList();

        // Platform Service에서 예매 스냅샷용 공연 회차 정보 조회
        ReservationEventSessionInfo eventSessionInfo = eventSessionQueryPort.getReservationInfo(eventSessionId);
        validateEventSessionInfo(eventSessionId, eventSessionInfo);

        // 검증·조회한 값으로 예매 생성 및 저장
        Reservation reservation = Reservation.create(
                command.getLoginUserId(), eventSessionInfo.eventId(), eventSessionId, generateReservationNumber(),
                eventSessionInfo.eventTitle(), eventSessionInfo.sessionStartAt().toInstant(), seatCount, totalAmount,
                command.getIdempotencyKey(), reservationSeatCreationData
        );
        Reservation savedReservation = reservationRepositoryPort.save(reservation);

        // TODO: SeatHold 만료 시각 연장
        // TODO: Payment 결제 생성 API 호출

        return new CreateReservationResult(savedReservation, true);
    }

    private void validateReadAuthority(GetReservationCommand command, Reservation reservation) {
        if (ADMIN_ROLE.equals(command.getUserRole())) {
            return;
        }

        if (USER_ROLE.equals(command.getUserRole())
                && Objects.equals(command.getLoginUserId(), reservation.getUserId())) {
            return;
        }

        throw new BusinessException(ReservationErrorCode.RESERVATION_NOT_FOUND);
    }

    private void validatePaymentAuthority(PaymentValidationCommand command, Reservation reservation) {
        if (!Objects.equals(command.getUserId(), reservation.getUserId())) {
            throw new BusinessException(ReservationErrorCode.RESERVATION_NOT_FOUND);
        }
    }

    private void validateSeatHolds(Long userId, List<UUID> seatHoldIds, List<SeatHoldValidationInfo> seatHolds) {
        Set<UUID> foundSeatHoldIds = seatHolds.stream()
                .map(SeatHoldValidationInfo::seatHoldId).collect(Collectors.toSet());

        if (foundSeatHoldIds.size() != seatHoldIds.size() || !foundSeatHoldIds.containsAll(seatHoldIds)) {
            throw new BusinessException(ReservationErrorCode.SEAT_HOLD_NOT_FOUND);
        }
        if (seatHolds.stream().anyMatch(seatHold -> !Objects.equals(seatHold.userId(), userId))) {
            throw new BusinessException(ReservationErrorCode.SEAT_HOLD_OWNERSHIP_REQUIRED);
        }
        if (seatHolds.stream().anyMatch(seatHold -> seatHold.holdStatus() != HoldStatus.HOLDING)) {
            throw new BusinessException(ReservationErrorCode.INVALID_SEAT_HOLD_STATUS);
        }

        Instant now = Instant.now(); // 모든 좌석에 동일 현재 시각 기준 검증
        if (seatHolds.stream().anyMatch(seatHold -> !seatHold.expiresAt().isAfter(now))) {
            throw new BusinessException(ReservationErrorCode.SEAT_HOLD_EXPIRED);
        }
    }

    // 예매 생성 요청에 포함된 좌석 선점 ID 검증
    private void validateRequestedSeatHoldIds(List<UUID> seatHoldIds) {
        if (seatHoldIds == null || seatHoldIds.isEmpty() || seatHoldIds.stream().anyMatch(seatHoldId -> seatHoldId == null)) {
            throw new BusinessException(ReservationErrorCode.INVALID_INPUT);
        }
        if (seatHoldIds.stream().distinct().count() != seatHoldIds.size()) {
            throw new BusinessException(ReservationErrorCode.INVALID_INPUT);
        }
    }

    // 예매 생성용 좌석 정보를 조회하고 검증
    private List<ReservationCreationSeatInfo> findValidatedCreationSeats(Long loginUserId, List<UUID> seatHoldIds) {
        List<ReservationCreationSeatInfo> seatInfos =
                seatHoldQueryPort.findCreationInfosBySeatHoldIds(seatHoldIds);
        validateCreationSeats(loginUserId, seatHoldIds, seatInfos);

        return seatInfos;
    }

    // 기존 예매와 동일한 좌석 선점 목록을 사용한 멱등 요청인지 검증
    private void validateIdempotentRequest(Reservation existingReservation, List<UUID> seatHoldIds) {
        Set<UUID> existingSeatHoldIds = existingReservation.getReservationSeats().stream()
                .map(ReservationSeat::getSeatHoldId).collect(Collectors.toSet());

        if (existingSeatHoldIds.size() != seatHoldIds.size() || !existingSeatHoldIds.containsAll(seatHoldIds)) {
            throw new BusinessException(ReservationErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
    }

    // Platform Service 응답이 요청한 공연 회차 정보인지 검증
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

    // 조회된 좌석 정보가 예매 생성 조건을 충족하는지 검증
    private void validateCreationSeats(Long loginUserId, List<UUID> seatHoldIds, List<ReservationCreationSeatInfo> seatInfos) {

        // 요청한 좌석 선점 정보가 모두 조회되었는지 검증
        Set<UUID> foundSeatHoldIds = seatInfos.stream()
                .map(ReservationCreationSeatInfo::seatHoldId).collect(Collectors.toSet());

        if (foundSeatHoldIds.size() != seatHoldIds.size() || !foundSeatHoldIds.containsAll(seatHoldIds)) {
            throw new BusinessException(ReservationErrorCode.SEAT_HOLD_NOT_FOUND);
        }

        // 좌석 선점 소유자 검증
        if (seatInfos.stream().anyMatch(seatInfo -> !Objects.equals(seatInfo.userId(), loginUserId))) {
            throw new BusinessException(ReservationErrorCode.SEAT_HOLD_OWNERSHIP_REQUIRED);
        }

        // 좌석 선점 상태 검증
        if (seatInfos.stream().anyMatch(seatInfo -> seatInfo.holdStatus() != HoldStatus.HOLDING)) {
            throw new BusinessException(ReservationErrorCode.INVALID_SEAT_HOLD_STATUS);
        }

        // 좌석 선점 만료 여부 검증
        Instant now = Instant.now();
        if (seatInfos.stream().anyMatch(seatInfo -> !seatInfo.expiresAt().isAfter(now))) {
            throw new BusinessException(ReservationErrorCode.SEAT_HOLD_EXPIRED);
        }

        // 모든 좌석이 동일한 공연 회차인지 검증
        validateSingleEventSession(seatInfos);

        // 이미 예매에 사용된 좌석 선점인지 검증
        List<UUID> usedSeatHoldIds = reservationRepositoryPort.findUsedSeatHoldIds(seatHoldIds);
        if (!usedSeatHoldIds.isEmpty()) {
            throw new BusinessException(ReservationErrorCode.RESERVATION_ALREADY_EXISTS);
        }
    }

    // 조회된 좌석이 하나의 공연 회차에 속하는지 검증
    private void validateSingleEventSession(List<ReservationCreationSeatInfo> seatInfos) {
        Set<UUID> eventSessionIds = seatInfos.stream()
                .map(ReservationCreationSeatInfo::eventSessionId).collect(Collectors.toSet());

        if (eventSessionIds.size() != 1 || eventSessionIds.contains(null)) {
            throw new BusinessException(ReservationErrorCode.INVALID_INPUT);
        }
    }

    // 예매 대상 공연 회차 ID 추출
    private UUID resolveEventSessionId(List<ReservationCreationSeatInfo> seatInfos) {
        return seatInfos.get(0).eventSessionId();
    }

    // 예매 총금액 계산
    private long calculateTotalAmount(List<ReservationCreationSeatInfo> seatInfos) {
        return seatInfos.stream().mapToLong(ReservationCreationSeatInfo::price).sum();
    }

    private Long resolveOwnerUserId(SearchReservationsCommand command) {
        if (ADMIN_ROLE.equals(command.getUserRole())) {
            return null;
        }
        if (USER_ROLE.equals(command.getUserRole()) && command.getLoginUserId() != null) {
            return command.getLoginUserId();
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }

    private String normalizeEventTitle(String eventTitle) {
        return eventTitle == null || eventTitle.isBlank() ? null : eventTitle.trim();
    }

    private ReservationStatus resolveReservationStatus(String reservationStatus) {
        if (reservationStatus == null || reservationStatus.isBlank()) {
            return null;
        }

        try {
            return ReservationStatus.valueOf(reservationStatus);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private Pageable normalizePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), DEFAULT_PAGE);
        int size = normalizePageSize(pageable.getPageSize());
        List<Sort.Order> sortOrders = pageable.getSort().stream().toList();

        Sort.Order sortOrder;
        if (sortOrders.isEmpty()) {
            sortOrder = Sort.Order.desc("createdAt");
        }
        else {
            if (sortOrders.size() != 1) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            sortOrder = sortOrders.get(0);
        }

        if (!ALLOWED_SORT_FIELDS.contains(sortOrder.getProperty())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        return PageRequest.of(page, size, Sort.by(sortOrder));
    }

    private int normalizePageSize(int size) {
        return switch (size) {
            case 10, 30, 50 -> size;
            default -> DEFAULT_SIZE;
        };
    }

    private String generateReservationNumber() {
        String date = LocalDate.now(SEOUL_ZONE_ID).format(RESERVATION_DATE_FORMATTER);
        String randomPart = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);

        return "RSV-" + date + "-" + randomPart; // ex. RSV-260902-8F3A91C2D7E4
    }
}
