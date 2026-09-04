package com.tikitaka.ticketing.reservation.application;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.exception.CommonErrorCode;
import com.tikitaka.ticketing.reservation.application.command.GetReservationCommand;
import com.tikitaka.ticketing.reservation.application.command.PaymentValidationCommand;
import com.tikitaka.ticketing.reservation.application.command.SearchReservationsCommand;
import com.tikitaka.ticketing.reservation.application.result.PaymentValidationResult;
import com.tikitaka.ticketing.reservation.application.result.ReservationResult;
import com.tikitaka.ticketing.reservation.application.result.ReservationSearchResult;
import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import com.tikitaka.ticketing.reservation.domain.model.ReservationSeatInfo;
import com.tikitaka.ticketing.reservation.domain.model.SeatHoldValidationInfo;
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

    public ReservationService(ReservationRepositoryPort reservationRepositoryPort, SeatHoldQueryPort seatHoldQueryPort) {
        this.reservationRepositoryPort = reservationRepositoryPort;
        this.seatHoldQueryPort = seatHoldQueryPort;
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
