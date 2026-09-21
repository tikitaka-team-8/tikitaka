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
import com.tikitaka.ticketing.reservation.application.result.ReservationCreationPreparation;
import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import com.tikitaka.ticketing.reservation.domain.model.PaymentCreationInfo;
import com.tikitaka.ticketing.reservation.domain.model.ReservationSeatInfo;
import com.tikitaka.ticketing.reservation.domain.model.SeatHoldValidationInfo;
import com.tikitaka.ticketing.reservation.domain.port.PaymentCreationPort;
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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReservationService {
    private static final String USER_ROLE = "USER";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "sessionStartAt");

    private final ReservationRepositoryPort reservationRepositoryPort;
    private final SeatHoldQueryPort seatHoldQueryPort;
    private final PaymentCreationPort paymentCreationPort;
    private final ReservationCreationTransactionService reservationCreationTransactionService;

    public ReservationService(ReservationRepositoryPort reservationRepositoryPort,
            SeatHoldQueryPort seatHoldQueryPort, PaymentCreationPort paymentCreationPort,
            ReservationCreationTransactionService reservationCreationTransactionService) {

        this.reservationRepositoryPort = reservationRepositoryPort;
        this.seatHoldQueryPort = seatHoldQueryPort;
        this.paymentCreationPort = paymentCreationPort;
        this.reservationCreationTransactionService = reservationCreationTransactionService;
    }

    @Transactional(readOnly = true)
    public ReservationResult getReservation(GetReservationCommand command) {

        Reservation reservation = reservationRepositoryPort.findById(command.getReservationId())
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        validateReadAuthority(command, reservation);

        List<ReservationSeatInfo> seatDetails =
                reservationRepositoryPort.findSeatDetailsByReservationId(reservation.getReservationId());

        return new ReservationResult(reservation, seatDetails);
    }

    @Transactional(readOnly = true)
    public Page<ReservationSearchResult> searchReservations(SearchReservationsCommand command) {

        // 입력 값 검증
        Long ownerUserId = resolveOwnerUserId(command);
        String eventTitle = normalizeEventTitle(command.getEventTitle());
        ReservationStatus reservationStatus = resolveReservationStatus(command.getReservationStatus());
        Pageable pageable = normalizePageable(command.getPageable());

        return reservationRepositoryPort.searchReservations(ownerUserId, eventTitle, reservationStatus, pageable)
                .map(ReservationSearchResult::new);
    }


    @Transactional(readOnly = true)
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
        validateReservedSeatHolds(command.getUserId(), seatHoldIds, seatHolds);

        return new PaymentValidationResult(reservation);
    }

    public CreateReservationResult createReservation(CreateReservationCommand command) {

        // 예매 의도와 좌석 상태를 먼저 커밋해 Payment 응답 유실 후에도 복구 기준을 남김
        ReservationCreationPreparation preparation = reservationCreationTransactionService.prepareReservation(command);
        if (!preparation.paymentCreationRequired()) {
            return preparation.reservationResult();
        }

        // DB 트랜잭션 밖에서 Payment를 호출해 외부 응답 지연이 예매 의도를 롤백하지 않도록 분리
        PaymentCreationInfo paymentCreationInfo = paymentCreationPort.createPayment(
                preparation.reservationId(), preparation.userId(), preparation.totalAmount(), preparation.idempotencyKey()
        );

        // Payment 응답을 검증하고 별도 트랜잭션에서 결제 연결을 완료
        return reservationCreationTransactionService.completePaymentCreation(
                preparation, paymentCreationInfo, command.getLoginUserId()
        );
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

    private void validateReservedSeatHolds(Long userId, List<UUID> seatHoldIds, List<SeatHoldValidationInfo> seatHolds) {
        Set<UUID> foundSeatHoldIds = seatHolds.stream()
                .map(SeatHoldValidationInfo::seatHoldId).collect(Collectors.toSet());

        if (foundSeatHoldIds.size() != seatHoldIds.size() || !foundSeatHoldIds.containsAll(seatHoldIds)) {
            throw new BusinessException(ReservationErrorCode.SEAT_HOLD_NOT_FOUND);
        }
        if (seatHolds.stream().anyMatch(seatHold -> !Objects.equals(seatHold.userId(), userId))) {
            throw new BusinessException(ReservationErrorCode.SEAT_HOLD_OWNERSHIP_REQUIRED);
        }
        if (seatHolds.stream().anyMatch(seatHold -> seatHold.holdStatus() != HoldStatus.RESERVED)) {
            throw new BusinessException(ReservationErrorCode.INVALID_SEAT_HOLD_STATUS);
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

}
