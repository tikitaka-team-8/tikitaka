package com.tikitaka.ticketing.reservation.application;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.exception.CommonErrorCode;
import com.tikitaka.ticketing.reservation.application.command.PaymentFailedCommand;
import com.tikitaka.ticketing.reservation.application.command.PaymentSucceededCommand;
import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.entity.ReservationInbox;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationFailureReason;
import com.tikitaka.ticketing.reservation.domain.port.ReservationInboxRepositoryPort;
import com.tikitaka.ticketing.reservation.domain.port.ReservationRepositoryPort;
import com.tikitaka.ticketing.reservation.exception.ReservationErrorCode;
import com.tikitaka.ticketing.seat.application.service.SeatHoldReservationValidator;
import com.tikitaka.ticketing.seat.domain.enums.ReleaseReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class ReservationPaymentEventService {
    private static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    private static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    private static final Long SYSTEM_USER_ID = 0L;

    private final ReservationRepositoryPort reservationRepositoryPort;
    private final ReservationInboxRepositoryPort reservationInboxRepositoryPort;
    private final ReservationOutboxService reservationOutboxService;
    private final SeatHoldReservationValidator seatHoldReservationValidator;

    public ReservationPaymentEventService(ReservationRepositoryPort reservationRepositoryPort,
            ReservationInboxRepositoryPort reservationInboxRepositoryPort,
            ReservationOutboxService reservationOutboxService,
            SeatHoldReservationValidator seatHoldReservationValidator) {
        this.reservationRepositoryPort = reservationRepositoryPort;
        this.reservationInboxRepositoryPort = reservationInboxRepositoryPort;
        this.reservationOutboxService = reservationOutboxService;
        this.seatHoldReservationValidator = seatHoldReservationValidator;
    }

    public boolean processPaymentSucceeded(PaymentSucceededCommand command) {

        validateEventId(command.getEventId());

        // 이미 처리한 이벤트이면 상태 변경 생략
        if (reservationInboxRepositoryPort.existsByEventId(command.getEventId())) {
            return false;
        }

        // 결제 성공 이벤트와 예매 정보의 정합성 검증
        Reservation reservation = findAndValidateReservation(
                command.getReservationId(), command.getPaymentId(), command.getUserId(), command.getAmount()
        );
        if (command.getApprovedAt() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        // 결제 성공 결과를 예매 상태에 반영
        boolean statusChanged = reservation.applyPaymentSucceeded(command.getApprovedAt(), SYSTEM_USER_ID);
        if (statusChanged) {
            // 예매 좌석의 SeatHold를 확정하고 ScheduleSeat를 판매 완료 상태로 변경
            reservation.getReservationSeats().forEach(
                    reservationSeat -> seatHoldReservationValidator.confirmHold(reservationSeat.getSeatHoldId())
            );
            reservationOutboxService.saveConfirmedEvent(reservation);
        }

        // 예매 상태 변경과 동일한 트랜잭션에서 처리 완료 이벤트 기록
        reservationInboxRepositoryPort.save(
                ReservationInbox.create(command.getEventId(), command.getReservationId(), PAYMENT_SUCCEEDED)
        );
        return statusChanged;
    }

    public boolean processPaymentFailed(PaymentFailedCommand command) {

        validateEventId(command.getEventId());

        // 이미 처리한 이벤트이면 상태 변경 생략
        if (reservationInboxRepositoryPort.existsByEventId(command.getEventId())) {
            return false;
        }

        // 결제 실패 이벤트와 예매 정보의 정합성 검증
        Reservation reservation = findAndValidateReservation(
                command.getReservationId(), command.getPaymentId(), command.getUserId(), command.getAmount()
        );

        // 결제 실패 결과를 예매에 반영
        boolean statusChanged = reservation.applyPaymentFailed(ReservationFailureReason.PAYMENT_FAILED, SYSTEM_USER_ID);
        if (statusChanged) {
            // 예매 좌석의 SeatHold를 해제하고 ScheduleSeat를 다시 예매 가능한 상태로 변경
            reservation.getReservationSeats().forEach(
                    reservationSeat -> seatHoldReservationValidator.releaseHold(
                            reservationSeat.getSeatHoldId(), ReleaseReason.PAYMENT_FAILED
                    )
            );
            reservationOutboxService.saveFailedEvent(reservation);
        }

        // 예매 상태 변경과 동일한 트랜잭션에서 처리 완료 이벤트 기록
        reservationInboxRepositoryPort.save(
                ReservationInbox.create(command.getEventId(), command.getReservationId(), PAYMENT_FAILED)
        );
        return statusChanged;
    }

    private void validateEventId(UUID eventId) {
        if (eventId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private Reservation findAndValidateReservation(UUID reservationId, UUID paymentId, Long userId, Long amount) {
        if (reservationId == null || paymentId == null || userId == null || amount == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        Reservation reservation = reservationRepositoryPort.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        if (!Objects.equals(reservation.getPaymentId(), paymentId)
                || !Objects.equals(reservation.getUserId(), userId)
                || !Objects.equals(reservation.getTotalAmount(), amount)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return reservation;
    }
}
