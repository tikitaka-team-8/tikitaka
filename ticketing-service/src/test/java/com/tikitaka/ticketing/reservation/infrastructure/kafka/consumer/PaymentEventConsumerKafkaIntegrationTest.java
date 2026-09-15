package com.tikitaka.ticketing.reservation.infrastructure.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.ticketing.reservation.infrastructure.kafka.KafkaTopics;
import com.tikitaka.ticketing.reservation.infrastructure.kafka.event.PaymentFailedEvent;
import com.tikitaka.ticketing.reservation.infrastructure.kafka.event.PaymentSucceededEvent;
import com.tikitaka.ticketing.testsupport.PostgresIntegrationTest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

@PostgresIntegrationTest
@EmbeddedKafka(
        partitions = 1,
        topics = {
                KafkaTopics.PAYMENT_EVENTS,
                KafkaTopics.PAYMENT_EVENTS_DLT,
                KafkaTopics.RESERVATION_EVENTS
        },
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "reservation.kafka.consumer.auto-startup=true"
})
class PaymentEventConsumerKafkaIntegrationTest {

    private static final String CONSUMER_GROUP = "ticketing-payment-group";
    private static final long USER_ID = 91_000_001L;
    private static final long AMOUNT = 150_000L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM p_reservation_inbox");
        jdbcTemplate.update("DELETE FROM p_reservation_outbox");
        jdbcTemplate.update("DELETE FROM p_reservation_seats");
        jdbcTemplate.update("DELETE FROM p_reservation");
        jdbcTemplate.update("DELETE FROM p_seat_hold");
        jdbcTemplate.update("DELETE FROM p_schedule_seat");
    }

    @Test
    void 동일한_결제_성공_이벤트를_두번_소비해도_예매와_Inbox_Outbox는_한번만_변경된다() throws Exception {
        Fixture fixture = insertPaymentProcessingFixture();
        UUID eventId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        String payload = serialize(new PaymentSucceededEvent(
                eventId,
                "PAYMENT_SUCCEEDED",
                occurredAt,
                fixture.reservationId(),
                1,
                fixture.paymentId(),
                fixture.reservationId(),
                USER_ID,
                AMOUNT,
                occurredAt
        ));

        long committedOffset = publishTwiceAndWaitUntilCommitted(fixture.reservationId(), payload);

        assertThat(committedOffset).isGreaterThanOrEqualTo(2L);
        assertThat(queryString("SELECT reservation_status FROM p_reservation WHERE reservation_id = ?", fixture.reservationId()))
                .isEqualTo("CONFIRMED");
        assertThat(queryLong("SELECT version FROM p_reservation WHERE reservation_id = ?", fixture.reservationId()))
                .isEqualTo(1L);
        assertThat(queryString("SELECT hold_status FROM p_seat_hold WHERE seat_hold_id = ?", fixture.seatHoldId()))
                .isEqualTo("CONFIRMED");
        assertThat(queryString("SELECT seat_status FROM p_schedule_seat WHERE schedule_seat_id = ?", fixture.scheduleSeatId()))
                .isEqualTo("SOLD");
        assertSingleInboxAndOutbox(eventId, fixture.reservationId(), "PAYMENT_SUCCEEDED", "RESERVATION_CONFIRMED");
        assertThat(topicEndOffset(KafkaTopics.PAYMENT_EVENTS_DLT)).isZero();
    }

    @Test
    void 동일한_결제_실패_이벤트를_두번_소비해도_예매와_Inbox_Outbox는_한번만_변경된다() throws Exception {
        Fixture fixture = insertPaymentProcessingFixture();
        UUID eventId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        String payload = serialize(new PaymentFailedEvent(
                eventId,
                "PAYMENT_FAILED",
                occurredAt,
                fixture.reservationId(),
                1,
                fixture.paymentId(),
                fixture.reservationId(),
                USER_ID,
                AMOUNT,
                "PAYMENT_APPROVAL_FAILED",
                occurredAt
        ));

        long committedOffset = publishTwiceAndWaitUntilCommitted(fixture.reservationId(), payload);

        assertThat(committedOffset).isGreaterThanOrEqualTo(2L);
        assertThat(queryString("SELECT reservation_status FROM p_reservation WHERE reservation_id = ?", fixture.reservationId()))
                .isEqualTo("FAILED");
        assertThat(queryLong("SELECT version FROM p_reservation WHERE reservation_id = ?", fixture.reservationId()))
                .isEqualTo(1L);
        assertThat(queryString("SELECT hold_status FROM p_seat_hold WHERE seat_hold_id = ?", fixture.seatHoldId()))
                .isEqualTo("RELEASED");
        assertThat(queryString("SELECT seat_status FROM p_schedule_seat WHERE schedule_seat_id = ?", fixture.scheduleSeatId()))
                .isEqualTo("AVAILABLE");
        assertSingleInboxAndOutbox(eventId, fixture.reservationId(), "PAYMENT_FAILED", "RESERVATION_FAILED");
        assertThat(topicEndOffset(KafkaTopics.PAYMENT_EVENTS_DLT)).isZero();
    }

    private Fixture insertPaymentProcessingFixture() {
        UUID reservationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID eventSessionId = UUID.randomUUID();
        UUID scheduleSeatId = UUID.randomUUID();
        UUID venueSeatId = UUID.randomUUID();
        UUID seatHoldId = UUID.randomUUID();
        UUID reservationSeatId = UUID.randomUUID();
        UUID holdToken = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO p_schedule_seat (
                    schedule_seat_id, event_session_id, venue_seat_id,
                    section, row_label, seat_number, seat_grade, price, seat_status,
                    created_at, created_by, updated_at, updated_by
                ) VALUES (?, ?, ?, 'VIP', 'A', '1', 'VIP', ?, 'HELD', CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?)
                """, scheduleSeatId, eventSessionId, venueSeatId, AMOUNT, USER_ID, USER_ID);

        jdbcTemplate.update("""
                INSERT INTO p_seat_hold (
                    seat_hold_id, schedule_seat_id, user_id, hold_token,
                    hold_status, held_at, expires_at,
                    created_at, created_by, updated_at, updated_by,
                    idempotency_key, reserved_at
                ) VALUES (?, ?, ?, ?, 'RESERVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour',
                          CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, ?, CURRENT_TIMESTAMP)
                """, seatHoldId, scheduleSeatId, USER_ID, holdToken, USER_ID, USER_ID, "integration-hold-" + seatHoldId);

        jdbcTemplate.update("""
                INSERT INTO p_reservation (
                    reservation_id, user_id, event_id, event_session_id, payment_id,
                    reservation_number, event_title, session_start_at,
                    seat_count, total_amount, reservation_status, idempotency_key,
                    created_at, created_by, updated_at, updated_by, is_deleted, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'Kafka integration test', CURRENT_TIMESTAMP + INTERVAL '1 day',
                          1, ?, 'PAYMENT_PROCESSING', ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, FALSE, 0)
                """, reservationId, USER_ID, eventId, eventSessionId, paymentId,
                "IT-" + reservationId.toString().substring(0, 8), AMOUNT,
                "integration-reservation-" + reservationId, USER_ID, USER_ID);

        jdbcTemplate.update("""
                INSERT INTO p_reservation_seats (
                    reservation_seat_id, reservation_id, seat_hold_id, schedule_seat_id,
                    price, created_at, created_by, updated_at, updated_by, is_deleted
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, FALSE)
                """, reservationSeatId, reservationId, seatHoldId, scheduleSeatId, AMOUNT, USER_ID, USER_ID);

        return new Fixture(reservationId, paymentId, scheduleSeatId, seatHoldId);
    }

    private long publishTwiceAndWaitUntilCommitted(UUID reservationId, String payload) throws Exception {
        SendResult<String, String> first = kafkaTemplate
                .send(KafkaTopics.PAYMENT_EVENTS, reservationId.toString(), payload)
                .get(10, TimeUnit.SECONDS);
        SendResult<String, String> second = kafkaTemplate
                .send(KafkaTopics.PAYMENT_EVENTS, reservationId.toString(), payload)
                .get(10, TimeUnit.SECONDS);
        long expectedCommittedOffset = second.getRecordMetadata().offset() + 1;

        await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> committedOffset() >= expectedCommittedOffset);

        assertThat(second.getRecordMetadata().offset()).isEqualTo(first.getRecordMetadata().offset() + 1);
        return committedOffset();
    }

    private long committedOffset() throws Exception {
        TopicPartition topicPartition = new TopicPartition(KafkaTopics.PAYMENT_EVENTS, 0);
        try (AdminClient adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers
        ))) {
            Map<TopicPartition, OffsetAndMetadata> offsets = adminClient
                    .listConsumerGroupOffsets(CONSUMER_GROUP)
                    .partitionsToOffsetAndMetadata()
                    .get(5, TimeUnit.SECONDS);
            OffsetAndMetadata offset = offsets.get(topicPartition);
            return offset == null ? -1L : offset.offset();
        }
    }

    private long topicEndOffset(String topic) throws Exception {
        TopicPartition topicPartition = new TopicPartition(topic, 0);
        try (AdminClient adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers
        ))) {
            return adminClient.listOffsets(Map.of(
                    topicPartition, org.apache.kafka.clients.admin.OffsetSpec.latest()
            )).partitionResult(topicPartition).get(5, TimeUnit.SECONDS).offset();
        }
    }

    private void assertSingleInboxAndOutbox(UUID sourceEventId, UUID reservationId,
            String inboxEventType, String outboxEventType) {
        assertThat(queryLong("SELECT COUNT(*) FROM p_reservation_inbox WHERE event_id = ?", sourceEventId))
                .isEqualTo(1L);
        assertThat(queryLong("""
                SELECT COUNT(*) FROM p_reservation_inbox
                WHERE reservation_id = ? AND event_type = ?
                """, reservationId, inboxEventType)).isEqualTo(1L);
        assertThat(queryLong("""
                SELECT COUNT(*) FROM p_reservation_outbox
                WHERE reservation_id = ? AND event_type = ?
                """, reservationId, outboxEventType)).isEqualTo(1L);
    }

    private String serialize(Object event) throws JsonProcessingException {
        return objectMapper.writeValueAsString(event);
    }

    private String queryString(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, String.class, arguments);
    }

    private long queryLong(String sql, Object... arguments) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return result == null ? 0L : result;
    }

    private record Fixture(UUID reservationId, UUID paymentId, UUID scheduleSeatId, UUID seatHoldId) {
    }
}
