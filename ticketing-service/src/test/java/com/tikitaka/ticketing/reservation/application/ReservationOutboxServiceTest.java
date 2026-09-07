package com.tikitaka.ticketing.reservation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.entity.ReservationOutbox;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationFailureReason;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationOutboxEventType;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationOutboxStatus;
import com.tikitaka.ticketing.reservation.domain.port.ReservationOutboxRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationOutboxServiceTest {

    private static final UUID RESERVATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PAYMENT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_SESSION_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Long USER_ID = 1L;
    private static final Long AMOUNT = 50_000L;
    private static final Instant APPROVED_AT = Instant.parse("2026-09-06T10:00:00Z");

    @Mock
    private ReservationOutboxRepositoryPort reservationOutboxRepositoryPort;

    private ObjectMapper objectMapper;
    private ReservationOutboxService reservationOutboxService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        reservationOutboxService = new ReservationOutboxService(reservationOutboxRepositoryPort, objectMapper);
    }

    @Test
    void 예매_확정_이벤트를_Pending_Outbox로_저장한다() throws Exception {
        // given
        Reservation reservation = createPaymentProcessingReservation();
        reservation.applyPaymentSucceeded(APPROVED_AT, 0L);

        // when
        reservationOutboxService.saveConfirmedEvent(reservation);

        // then
        ArgumentCaptor<ReservationOutbox> outboxCaptor = ArgumentCaptor.forClass(ReservationOutbox.class);
        verify(reservationOutboxRepositoryPort).save(outboxCaptor.capture());

        ReservationOutbox outbox = outboxCaptor.getValue();
        JsonNode payload = objectMapper.readTree(outbox.getPayload());

        assertThat(outbox.getEventId()).isNotNull();
        assertThat(outbox.getReservationId()).isEqualTo(RESERVATION_ID);
        assertThat(outbox.getEventType()).isEqualTo(ReservationOutboxEventType.RESERVATION_CONFIRMED);
        assertThat(outbox.getStatus()).isEqualTo(ReservationOutboxStatus.PENDING);
        assertThat(outbox.getCreatedAt()).isNotNull();
        assertThat(outbox.getPublishedAt()).isNull();
        assertThat(payload.get("eventId").asText()).isEqualTo(outbox.getEventId().toString());
        assertThat(payload.get("eventType").asText()).isEqualTo("RESERVATION_CONFIRMED");
        assertThat(payload.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(payload.get("reservationId").asText()).isEqualTo(RESERVATION_ID.toString());
        assertThat(payload.get("reservationNumber").asText()).isEqualTo("RSV-260906-123456789ABC");
        assertThat(payload.get("userId").asLong()).isEqualTo(USER_ID);
    }

    @Test
    void 예매_실패_이벤트에_실패사유를_포함하여_Outbox로_저장한다() throws Exception {
        // given
        Reservation reservation = createPaymentProcessingReservation();
        reservation.applyPaymentFailed(ReservationFailureReason.PAYMENT_FAILED, 0L);

        // when
        reservationOutboxService.saveFailedEvent(reservation);

        // then
        ArgumentCaptor<ReservationOutbox> outboxCaptor = ArgumentCaptor.forClass(ReservationOutbox.class);
        verify(reservationOutboxRepositoryPort).save(outboxCaptor.capture());

        ReservationOutbox outbox = outboxCaptor.getValue();
        JsonNode payload = objectMapper.readTree(outbox.getPayload());

        assertThat(outbox.getEventType()).isEqualTo(ReservationOutboxEventType.RESERVATION_FAILED);
        assertThat(outbox.getStatus()).isEqualTo(ReservationOutboxStatus.PENDING);
        assertThat(payload.get("eventId").asText()).isEqualTo(outbox.getEventId().toString());
        assertThat(payload.get("eventType").asText()).isEqualTo("RESERVATION_FAILED");
        assertThat(payload.get("reservationId").asText()).isEqualTo(RESERVATION_ID.toString());
        assertThat(payload.get("failureReason").asText()).isEqualTo("PAYMENT_FAILED");
    }

    private Reservation createPaymentProcessingReservation() {
        Reservation reservation = Reservation.create(
                USER_ID,
                UUID.randomUUID(),
                EVENT_SESSION_ID,
                "RSV-260906-123456789ABC",
                "테스트 공연",
                Instant.parse("2026-09-07T10:00:00Z"),
                1,
                AMOUNT,
                "reservation-outbox-test",
                List.of()
        );
        ReflectionTestUtils.setField(reservation, "reservationId", RESERVATION_ID);
        reservation.markAsPaymentProcessing(PAYMENT_ID, USER_ID);
        return reservation;
    }
}
