package com.tikitaka.ticketing.reservation.infrastructure.kafka.outbox;

import com.tikitaka.ticketing.reservation.domain.entity.ReservationOutbox;
import com.tikitaka.ticketing.reservation.domain.port.ReservationOutboxRepositoryPort;
import com.tikitaka.ticketing.reservation.infrastructure.kafka.producer.ReservationEventProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class ReservationOutboxPublisher {

    private final ReservationOutboxRepositoryPort reservationOutboxRepositoryPort;
    private final ReservationEventProducer reservationEventProducer;

    public ReservationOutboxPublisher(ReservationOutboxRepositoryPort reservationOutboxRepositoryPort,
            ReservationEventProducer reservationEventProducer) {

        this.reservationOutboxRepositoryPort = reservationOutboxRepositoryPort;
        this.reservationEventProducer = reservationEventProducer;
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void publish() {

        // 발행 대기 중인 예매 결과 이벤트 조회
        List<ReservationOutbox> outboxes = reservationOutboxRepositoryPort.findPendingOutboxes();
        if (outboxes.isEmpty()) {
            return;
        }

        // 저장된 payload를 변경하지 않고 순서대로 Kafka 발행
        for (ReservationOutbox outbox : outboxes) {
            try {
                reservationEventProducer.send(outbox.getReservationId().toString(), outbox.getPayload());
                outbox.markAsPublished(Instant.now());

                log.info("Reservation Outbox 발행 완료: eventId={}, reservationId={}, eventType={}",
                        outbox.getEventId(), outbox.getReservationId(), outbox.getEventType());
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.error("Reservation Outbox 발행 중단: eventId={}, reservationId={}, eventType={}",
                        outbox.getEventId(), outbox.getReservationId(), outbox.getEventType(), exception);
                return;
            }
            catch (Exception exception) {
                log.error("Reservation Outbox 발행 실패: eventId={}, reservationId={}, eventType={}",
                        outbox.getEventId(), outbox.getReservationId(), outbox.getEventType(), exception);
            }
        }
    }
}
