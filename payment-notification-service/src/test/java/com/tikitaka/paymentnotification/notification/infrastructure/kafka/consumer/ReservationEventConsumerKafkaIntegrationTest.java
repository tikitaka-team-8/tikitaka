package com.tikitaka.paymentnotification.notification.infrastructure.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.paymentnotification.global.exception.BusinessException;
import com.tikitaka.paymentnotification.notification.infrastructure.kafka.KafkaTopics;
import com.tikitaka.paymentnotification.notification.infrastructure.kafka.event.ReservationConfirmedEvent;
import com.tikitaka.paymentnotification.notification.infrastructure.kafka.event.ReservationFailedEvent;
import com.tikitaka.paymentnotification.testsupport.PostgresIntegrationTest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
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
        long dltOffsetBefore = topicEndOffset(KafkaTopics.RESERVATION_EVENTS_DLT);
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
        assertThat(topicEndOffset(KafkaTopics.RESERVATION_EVENTS_DLT)).isEqualTo(dltOffsetBefore);
    }

    @Test
    void 동일한_예매_실패_이벤트를_두번_소비해도_알림과_Inbox는_한번만_생성된다() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        long dltOffsetBefore = topicEndOffset(KafkaTopics.RESERVATION_EVENTS_DLT);
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
        assertThat(topicEndOffset(KafkaTopics.RESERVATION_EVENTS_DLT)).isEqualTo(dltOffsetBefore);
    }

    @Test
    void 지원하지_않는_예매_이벤트는_원본_정보를_보존하여_DLT로_이동한다() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        long dltOffsetBefore = topicEndOffset(KafkaTopics.RESERVATION_EVENTS_DLT);
        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "UNSUPPORTED_RESERVATION_EVENT",
                  "reservationId": "%s"
                }
                """.formatted(eventId, reservationId);

        SendResult<String, String> original = kafkaTemplate
                .send(KafkaTopics.RESERVATION_EVENTS, reservationId.toString(), payload)
                .get(10, TimeUnit.SECONDS);
        ConsumerRecord<String, String> dltRecord = consumeDltRecord(
                KafkaTopics.RESERVATION_EVENTS_DLT,
                dltOffsetBefore
        );

        assertThat(dltRecord.partition()).isEqualTo(original.getRecordMetadata().partition());
        assertThat(dltRecord.key()).isEqualTo(reservationId.toString());
        assertThat(dltRecord.value()).isEqualTo(payload);
        assertThat(headerString(dltRecord, KafkaHeaders.DLT_ORIGINAL_TOPIC))
                .isEqualTo(KafkaTopics.RESERVATION_EVENTS);
        assertThat(headerInt(dltRecord, KafkaHeaders.DLT_ORIGINAL_PARTITION))
                .isEqualTo(original.getRecordMetadata().partition());
        assertThat(headerLong(dltRecord, KafkaHeaders.DLT_ORIGINAL_OFFSET))
                .isEqualTo(original.getRecordMetadata().offset());
        assertThat(headerString(dltRecord, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP))
                .isEqualTo(CONSUMER_GROUP);
        assertThat(headerString(dltRecord, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .isEqualTo(BusinessException.class.getName());

        await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> committedOffset() >= original.getRecordMetadata().offset() + 1);
        assertThat(queryLong("SELECT COUNT(*) FROM p_notification_inbox WHERE event_id = ?", eventId)).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM p_notification WHERE source_event_id = ?", eventId)).isZero();
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

    private ConsumerRecord<String, String> consumeDltRecord(String topic, long startOffset) {
        TopicPartition topicPartition = new TopicPartition(topic, 0);
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "notification-dlt-verifier-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
        );

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.assign(List.of(topicPartition));
            consumer.seek(topicPartition, startOffset);
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();

            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    return record;
                }
            }
        }
        throw new AssertionError("DLT 레코드를 제한 시간 안에 소비하지 못했습니다: " + topic);
    }

    private String headerString(ConsumerRecord<String, String> record, String key) {
        return new String(requiredHeader(record, key).value(), StandardCharsets.UTF_8);
    }

    private int headerInt(ConsumerRecord<String, String> record, String key) {
        return ByteBuffer.wrap(requiredHeader(record, key).value()).getInt();
    }

    private long headerLong(ConsumerRecord<String, String> record, String key) {
        return ByteBuffer.wrap(requiredHeader(record, key).value()).getLong();
    }

    private Header requiredHeader(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        assertThat(header).as("Kafka DLT header %s", key).isNotNull();
        return header;
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
