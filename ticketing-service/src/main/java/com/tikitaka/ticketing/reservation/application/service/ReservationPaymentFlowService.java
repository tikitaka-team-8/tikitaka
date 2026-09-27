package com.tikitaka.ticketing.reservation.application.service;

import com.tikitaka.ticketing.reservation.application.command.PaymentFailedCommand;
import com.tikitaka.ticketing.reservation.application.command.PaymentSucceededCommand;
import com.tikitaka.ticketing.reservation.application.result.ReservationPaymentEventResult;
import com.tikitaka.ticketing.reservation.domain.port.ReservationQueueFlowPort;
import org.springframework.stereotype.Service;

@Service
public class ReservationPaymentFlowService {
    private final ReservationPaymentEventService reservationPaymentEventService;
    private final ReservationQueueFlowPort reservationQueueFlowPort;

    public ReservationPaymentFlowService(
            ReservationPaymentEventService reservationPaymentEventService,
            ReservationQueueFlowPort reservationQueueFlowPort) {

        this.reservationPaymentEventService = reservationPaymentEventService;
        this.reservationQueueFlowPort = reservationQueueFlowPort;
    }

    public boolean processPaymentSucceeded(PaymentSucceededCommand command) {
        ReservationPaymentEventResult result = reservationPaymentEventService.processPaymentSucceeded(command);

        // 예매 확정 트랜잭션이 커밋된 뒤 현재 Queue 입장 흐름을 종료
        completeReservationFlow(result);
        return result.statusChanged();
    }

    public boolean processPaymentFailed(PaymentFailedCommand command) {
        ReservationPaymentEventResult result = reservationPaymentEventService.processPaymentFailed(command);

        // 예매 실패 트랜잭션이 커밋된 뒤 현재 Queue 입장 흐름을 종료
        completeReservationFlow(result);
        return result.statusChanged();
    }

    private void completeReservationFlow(ReservationPaymentEventResult result) {
        reservationQueueFlowPort.completeReservationFlow(result.eventSessionId(), result.userId(), result.reservationId());
    }
}
