package com.tikitaka.ticketing.reservation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.ticketing.reservation.application.event.ReservationConfirmedEvent;
import com.tikitaka.ticketing.reservation.application.event.ReservationFailedEvent;
import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.entity.ReservationOutbox;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationOutboxEventType;
import com.tikitaka.ticketing.reservation.domain.port.ReservationOutboxRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class ReservationOutboxService {
    private static final int EVENT_VERSION = 1;

    private final ReservationOutboxRepositoryPort reservationOutboxRepositoryPort;
    private final ObjectMapper objectMapper;

    public ReservationOutboxService(ReservationOutboxRepositoryPort reservationOutboxRepositoryPort, ObjectMapper objectMapper) {
        this.reservationOutboxRepositoryPort = reservationOutboxRepositoryPort;
        this.objectMapper = objectMapper;
    }

    // 예매 확정 결과 이벤트와 Outbox 저장
    public void saveConfirmedEvent(Reservation reservation) {

        // 이벤트 식별값과 발생 시각 확정
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        // 예매 스냅샷으로 확정 이벤트 구성
        ReservationConfirmedEvent event = new ReservationConfirmedEvent(
                eventId, ReservationOutboxEventType.RESERVATION_CONFIRMED, EVENT_VERSION, occurredAt,
                reservation.getReservationId(), reservation.getReservationNumber(), reservation.getUserId(),
                reservation.getEventTitle(), reservation.getSessionStartAt()
        );

        // 이벤트와 동일한 식별값·발생 시각으로 Outbox 저장
        saveOutbox(eventId, reservation.getReservationId(), ReservationOutboxEventType.RESERVATION_CONFIRMED, serialize(event), occurredAt);
    }

    // 예매 실패 결과 이벤트와 Outbox 저장
    public void saveFailedEvent(Reservation reservation) {

        // 이벤트 식별값과 발생 시각 확정
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        // 예매 스냅샷과 실패 사유로 실패 이벤트 구성
        ReservationFailedEvent event = new ReservationFailedEvent(
                eventId, ReservationOutboxEventType.RESERVATION_FAILED, EVENT_VERSION, occurredAt,
                reservation.getReservationId(), reservation.getReservationNumber(), reservation.getUserId(),
                reservation.getEventTitle(), reservation.getSessionStartAt(), reservation.getFailureReason()
        );

        // 이벤트와 동일한 식별값·발생 시각으로 Outbox 저장
        saveOutbox(eventId, reservation.getReservationId(), ReservationOutboxEventType.RESERVATION_FAILED, serialize(event), occurredAt);
    }

    private void saveOutbox(UUID eventId, UUID reservationId, ReservationOutboxEventType eventType, String payload, Instant createdAt) {
        ReservationOutbox outbox = ReservationOutbox.create(eventId, reservationId, eventType, payload, createdAt);
        reservationOutboxRepositoryPort.save(outbox);
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("예매 결과 이벤트를 JSON으로 변환하지 못했습니다.", exception);
        }
    }
}
