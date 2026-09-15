package com.tikitaka.paymentnotification.notification.infrastructure.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.paymentnotification.notification.infrastructure.kafka.KafkaTopics;
import com.tikitaka.paymentnotification.notification.infrastructure.kafka.event.ReservationConfirmedEvent;
import com.tikitaka.paymentnotification.notification.infrastructure.kafka.event.ReservationFailedEvent;
import com.tikitaka.paymentnotification.testsupport.PostgresIntegrationTest;
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
                KafkaTopics.RESERVATION_EVENTS,
                KafkaTopics.RESERVATION_EVENTS_DLT
        },
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "notification.kafka.consumer.auto-startup=true"
})
class ReservationEventConsumerKafkaIntegrationTest {

    private static final String CONSUMER_GROUP = "notification-reservation-group";
    private static final long USER_ID = 91_000_001L;

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
        jdbcTemplate.update("DELETE FROM p_notification_inbox");
        jdbcTemplate.update("DELETE FROM p_notification");
    }

    @Test
    void 동일한_예매_확정_이벤트를_두번_소비해도_알림과_Inbox는_한번만_생성된다() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        String payload = serialize(new ReservationConfirmedEvent(
                eventId,
                "RESERVATION_CONFIRMED",
                1,
                occurredAt,
                reservationId,
                reservationNumber(reservationId),
                USER_ID,
                "Kafka integration test",
                occurredAt.plus(Duration.ofDays(1))
        ));

        long committedOffset = publishTwiceAndWaitUntilCommitted(reservationId, payload);

        assertThat(committedOffset).isGreaterThanOrEqualTo(2L);
        assertSingleNotificationAndInbox(eventId, reservationId, "RESERVATION_CONFIRMED");
        assertThat(queryString("SELECT read_status FROM p_notification WHERE source_event_id = ?", eventId))
                .isEqualTo("UNREAD");
        assertThat(topicEndOffset(KafkaTopics.RESERVATION_EVENTS_DLT)).isZero();
    }

    @Test
    void 동일한_예매_실패_이벤트를_두번_소비해도_알림과_Inbox는_한번만_생성된다() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        String payload = serialize(new ReservationFailedEvent(
                eventId,
                "RESERVATION_FAILED",
                1,
                occurredAt,
                reservationId,
                reservationNumber(reservationId),
                USER_ID,
                "Kafka integration test",
                occurredAt.plus(Duration.ofDays(1)),
                "PAYMENT_FAILED"
        ));

        long committedOffset = publishTwiceAndWaitUntilCommitted(reservationId, payload);

        assertThat(committedOffset).isGreaterThanOrEqualTo(2L);
        assertSingleNotificationAndInbox(eventId, reservationId, "RESERVATION_FAILED");
        assertThat(queryString("SELECT read_status FROM p_notification WHERE source_event_id = ?", eventId))
                .isEqualTo("UNREAD");
        assertThat(topicEndOffset(KafkaTopics.RESERVATION_EVENTS_DLT)).isZero();
    }

    private long publishTwiceAndWaitUntilCommitted(UUID reservationId, String payload) throws Exception {
        SendResult<String, String> first = kafkaTemplate
                .send(KafkaTopics.RESERVATION_EVENTS, reservationId.toString(), payload)
                .get(10, TimeUnit.SECONDS);
        SendResult<String, String> second = kafkaTemplate
                .send(KafkaTopics.RESERVATION_EVENTS, reservationId.toString(), payload)
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
        TopicPartition topicPartition = new TopicPartition(KafkaTopics.RESERVATION_EVENTS, 0);
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

    private void assertSingleNotificationAndInbox(UUID eventId, UUID reservationId, String eventType) {
        assertThat(queryLong("SELECT COUNT(*) FROM p_notification WHERE source_event_id = ?", eventId))
                .isEqualTo(1L);
        assertThat(queryLong("""
                SELECT COUNT(*) FROM p_notification
                WHERE reservation_id = ? AND notification_type = ?
                """, reservationId, eventType)).isEqualTo(1L);
        assertThat(queryLong("SELECT COUNT(*) FROM p_notification_inbox WHERE event_id = ?", eventId))
                .isEqualTo(1L);
        assertThat(queryLong("""
                SELECT COUNT(*) FROM p_notification_inbox
                WHERE reservation_id = ? AND event_type = ?
                """, reservationId, eventType)).isEqualTo(1L);
    }

    private String reservationNumber(UUID reservationId) {
        return "IT-" + reservationId.toString().substring(0, 8);
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
}
