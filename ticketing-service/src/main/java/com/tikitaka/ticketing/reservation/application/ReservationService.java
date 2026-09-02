package com.tikitaka.ticketing.reservation.application;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.exception.CommonErrorCode;
import com.tikitaka.ticketing.reservation.application.command.GetReservationCommand;
import com.tikitaka.ticketing.reservation.application.command.SearchReservationsCommand;
import com.tikitaka.ticketing.reservation.application.result.ReservationResult;
import com.tikitaka.ticketing.reservation.application.result.ReservationSearchResult;
import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import com.tikitaka.ticketing.reservation.domain.model.ReservationSeatDetail;
import com.tikitaka.ticketing.reservation.domain.port.ReservationRepositoryPort;
import com.tikitaka.ticketing.reservation.exception.ReservationErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional
public class ReservationService {
    private static final String USER_ROLE = "USER";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "sessionStartAt");

    private final ReservationRepositoryPort reservationRepositoryPort;

    public ReservationService(ReservationRepositoryPort reservationRepositoryPort) {
        this.reservationRepositoryPort = reservationRepositoryPort;
    }

    @Transactional(readOnly = true)
    public ReservationResult getReservation(GetReservationCommand command) {

        Reservation reservation = reservationRepositoryPort.findById(command.getReservationId())
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        validateReadAuthority(command, reservation);

        List<ReservationSeatDetail> seatDetails =
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
