package com.tikitaka.ticketing.reservation.infrastructure.kafka.outbox;

import com.tikitaka.ticketing.reservation.domain.entity.ReservationOutbox;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationOutboxEventType;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationOutboxStatus;
import com.tikitaka.ticketing.reservation.domain.port.ReservationOutboxRepositoryPort;
import com.tikitaka.ticketing.reservation.infrastructure.kafka.producer.ReservationEventProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReservationOutboxPublisherTest {

    private static final UUID EVENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String PAYLOAD = "{\"eventType\":\"RESERVATION_CONFIRMED\"}";

    @Mock
    private ReservationOutboxRepositoryPort reservationOutboxRepositoryPort;

    @Mock
    private ReservationEventProducer reservationEventProducer;

    @InjectMocks
    private ReservationOutboxPublisher reservationOutboxPublisher;

    @Test
    void Pending_Outbox를_Kafka로_발행하고_Published로_변경한다() throws Exception {
        // given
        ReservationOutbox outbox = createPendingOutbox();
        given(reservationOutboxRepositoryPort.findPendingOutboxes()).willReturn(List.of(outbox));

        // when
        reservationOutboxPublisher.publish();

        // then
        verify(reservationEventProducer).send(RESERVATION_ID.toString(), PAYLOAD);
        assertThat(outbox.getStatus()).isEqualTo(ReservationOutboxStatus.PUBLISHED);
        assertThat(outbox.getPublishedAt()).isNotNull();
    }

    @Test
    void Pending_Outbox가_없으면_Kafka를_호출하지_않는다() {
        // given
        given(reservationOutboxRepositoryPort.findPendingOutboxes()).willReturn(List.of());

        // when
        reservationOutboxPublisher.publish();

        // then
        verifyNoInteractions(reservationEventProducer);
    }

    @Test
    void Kafka_발행에_실패하면_Outbox를_Pending으로_유지한다() throws Exception {
        // given
        ReservationOutbox outbox = createPendingOutbox();
        given(reservationOutboxRepositoryPort.findPendingOutboxes()).willReturn(List.of(outbox));
        doThrow(new ExecutionException(new IllegalStateException("Kafka 발행 실패")))
                .when(reservationEventProducer).send(RESERVATION_ID.toString(), PAYLOAD);

        // when
        reservationOutboxPublisher.publish();

        // then
        assertThat(outbox.getStatus()).isEqualTo(ReservationOutboxStatus.PENDING);
        assertThat(outbox.getPublishedAt()).isNull();
    }

    private ReservationOutbox createPendingOutbox() {
        return ReservationOutbox.create(
                EVENT_ID,
                RESERVATION_ID,
                ReservationOutboxEventType.RESERVATION_CONFIRMED,
                PAYLOAD,
                Instant.parse("2026-09-07T10:00:00Z")
        );
    }
}
