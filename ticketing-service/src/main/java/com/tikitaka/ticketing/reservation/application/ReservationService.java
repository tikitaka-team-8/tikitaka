package com.tikitaka.ticketing.reservation.application;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.reservation.application.command.GetReservationCommand;
import com.tikitaka.ticketing.reservation.application.result.ReservationResult;
import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.model.ReservationSeatDetail;
import com.tikitaka.ticketing.reservation.domain.port.ReservationRepositoryPort;
import com.tikitaka.ticketing.reservation.exception.ReservationErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class ReservationService {
    private static final String USER_ROLE = "USER";
    private static final String ADMIN_ROLE = "ADMIN";

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
}
