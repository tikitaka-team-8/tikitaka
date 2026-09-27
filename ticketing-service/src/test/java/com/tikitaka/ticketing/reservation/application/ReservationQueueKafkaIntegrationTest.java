package com.tikitaka.ticketing.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.ticketing.queue.application.QueueService;
import com.tikitaka.ticketing.queue.application.QueueRepository;
import com.tikitaka.ticketing.queue.application.QueueAdmissionScheduler;
import com.tikitaka.ticketing.queue.application.PlatformSalesStatus;
import com.tikitaka.ticketing.queue.application.PlatformSalesStatusClient;
import com.tikitaka.ticketing.queue.domain.QueueEntry;
import com.tikitaka.ticketing.queue.domain.QueueStatus;
import com.tikitaka.ticketing.queue.domain.AdmissionToken;
import com.tikitaka.ticketing.queue.domain.AdmissionTokenStatus;
import com.tikitaka.ticketing.reservation.application.command.CreateReservationCommand;
import com.tikitaka.ticketing.reservation.application.service.ReservationService;
import com.tikitaka.ticketing.reservation.domain.model.PaymentCreationInfo;
import com.tikitaka.ticketing.reservation.domain.model.ReservationEventSessionInfo;
import com.tikitaka.ticketing.reservation.domain.port.EventSessionQueryPort;
import com.tikitaka.ticketing.reservation.domain.port.PaymentCreationPort;
import com.tikitaka.ticketing.reservation.domain.port.ReservationQueueFlowPort;
import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.queue.exception.QueueErrorCode;
import com.tikitaka.ticketing.reservation.infrastructure.kafka.KafkaTopics;
import com.tikitaka.ticketing.reservation.infrastructure.kafka.event.PaymentSucceededEvent;
import com.tikitaka.ticketing.reservation.infrastructure.kafka.event.PaymentFailedEvent;
import com.tikitaka.ticketing.testsupport.PostgresIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;

@PostgresIntegrationTest
@EmbeddedKafka(partitions = 1, topics = {KafkaTopics.PAYMENT_EVENTS,
        KafkaTopics.PAYMENT_EVENTS_DLT, KafkaTopics.RESERVATION_EVENTS},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = {"spring.kafka.consumer.auto-offset-reset=earliest",
        "reservation.kafka.consumer.auto-startup=true"})
@Import(ReservationQueueKafkaIntegrationTest.RedisConfiguration.class)
class ReservationQueueKafkaIntegrationTest {
    private static final long USER_ID = 95_000_002L;
    private static final long AMOUNT = 150_000L;
    private static final Duration TTL = Duration.ofHours(2);

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisConfiguration {
        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redisContainer() {
            return new GenericContainer<>("redis:7.2.14-alpine").withExposedPorts(6379);
        }
    }

    @Autowired private ReservationService reservationService;
    @Autowired private QueueService queueService;
    @Autowired private QueueRepository queueRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Value("${spring.kafka.bootstrap-servers}") private String bootstrapServers;

    // 외부 서비스와 비동기 스케줄러만 대체하고 Queue bind/complete는 실제 Redis로 실행
    @MockitoBean private EventSessionQueryPort eventSessionQueryPort;
    @MockitoBean private PaymentCreationPort paymentCreationPort;
    @MockitoBean private PlatformSalesStatusClient platformSalesStatusClient;
    @MockitoBean private QueueAdmissionScheduler queueAdmissionScheduler;
    @MockitoSpyBean private ReservationQueueFlowPort reservationQueueFlowPort;

    @ParameterizedTest(name = "결제 성공={0}, bind 실패={1}")
    @CsvSource({"true,false", "false,false", "true,true", "false,true"})
    void 예매_생성부터_결제_이벤트와_Queue_종료_정책을_검증한다(boolean succeeded, boolean bindFails) throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        OffsetDateTime time = OffsetDateTime.now();
        given(platformSalesStatusClient.getSalesStatus(sessionId)).willReturn(new PlatformSalesStatus(
                sessionId, "SCHEDULED", time.minusHours(1), time.plusHours(1), true));
        QueueEntry entered = enter(queueService.enterQueue(sessionId, USER_ID));
        if (bindFails) {
            // bind 호출에만 장애를 주입하며 이후 complete는 실제 Redis로 실행
            doThrow(new BusinessException(QueueErrorCode.QUEUE_SERVICE_UNAVAILABLE))
                    .when(reservationQueueFlowPort).bindReservationFlow(eq(sessionId), eq(USER_ID), any());
        }
        insertSeat(sessionId, seatId, holdId);
        given(eventSessionQueryPort.getReservationInfo(sessionId)).willReturn(new ReservationEventSessionInfo(
                sessionId, UUID.randomUUID(), "Queue 연동 검증", time.plusDays(1)));
        given(paymentCreationPort.createPayment(any(), any(), any(), any())).willAnswer(invocation ->
                new PaymentCreationInfo(paymentId, invocation.getArgument(0), "PAY-QUEUE", AMOUNT, "READY", time));

        // 실제 생성 트랜잭션과 Queue Adapter를 거쳐 Redis에 예매 식별자가 연결됨
        var reservation = reservationService.createReservation(new CreateReservationCommand(
                USER_ID, "USER", "queue-test-" + UUID.randomUUID(), List.of(holdId)));
        UUID reservationId = reservation.getReservationId();
        assertThat(queueRepository.findEntry(sessionId, USER_ID).orElseThrow().reservationId())
                .isEqualTo(bindFails ? null : reservationId);
        assertThat(reservation.getPaymentId()).isEqualTo(paymentId);
        assertThat(reservation.getReservationStatus().name()).isEqualTo("PAYMENT_PROCESSING");

        UUID eventId = UUID.randomUUID();
        Object event = succeeded
                ? new PaymentSucceededEvent(eventId, "PAYMENT_SUCCEEDED", time, reservationId, 1,
                        paymentId, reservationId, USER_ID, AMOUNT, time)
                : new PaymentFailedEvent(eventId, "PAYMENT_FAILED", time, reservationId, 1,
                        paymentId, reservationId, USER_ID, AMOUNT, "DECLINED", time);
        String payload = objectMapper.writeValueAsString(event);
        publishAndWait(reservationId, payload);

        assertThat(jdbcTemplate.queryForObject("SELECT reservation_status FROM p_reservation WHERE reservation_id = ?",
                String.class, reservationId)).isEqualTo(succeeded ? "CONFIRMED" : "FAILED");
        assertThat(jdbcTemplate.queryForObject("SELECT hold_status FROM p_seat_hold WHERE seat_hold_id = ?",
                String.class, holdId)).isEqualTo(succeeded ? "CONFIRMED" : "RELEASED");
        assertThat(jdbcTemplate.queryForObject("SELECT seat_status FROM p_schedule_seat WHERE schedule_seat_id = ?",
                String.class, seatId)).isEqualTo(succeeded ? "SOLD" : "AVAILABLE");
        assertThat(queueRepository.findEntry(sessionId, USER_ID).orElseThrow().status())
                .isEqualTo(bindFails ? QueueStatus.ENTERED : QueueStatus.EXPIRED);

        // 구매 종료 뒤 재등록은 새 WAITING이며, 이전 이벤트 재전달이 새 ENTERED를 종료하지 않음
        QueueEntry waiting = queueService.enterQueue(sessionId, USER_ID);
        QueueEntry newEntered;
        if (bindFails) {
            // 연결 누락 시 재대기가 생략되는 제한을 허용하며 자동 bind 복구는 하지 않음
            assertThat(waiting).isEqualTo(entered);
            newEntered = waiting;
        } else {
            assertThat(waiting.status()).isEqualTo(QueueStatus.WAITING);
            assertThat(waiting.sequence()).isGreaterThan(entered.sequence());
            newEntered = enter(waiting);
        }
        publishAndWait(reservationId, payload);
        assertThat(queueRepository.findEntry(sessionId, USER_ID).orElseThrow()).isEqualTo(newEntered);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM p_reservation_inbox WHERE event_id = ?",
                Long.class, eventId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM p_reservation_outbox WHERE reservation_id = ?",
                Long.class, reservationId)).isEqualTo(1L);
    }

    private QueueEntry enter(QueueEntry waiting) {
        Instant now = Instant.now();
        AdmissionToken token = new AdmissionToken(UUID.randomUUID().toString(), waiting.sessionId(), USER_ID,
                now.plusSeconds(180), AdmissionTokenStatus.ACTIVE);
        QueueEntry admitted = waiting.admit(now);
        assertThat(queueRepository.admitIfWaiting(admitted, token, TTL, Duration.ofMinutes(3))).isTrue();
        assertThat(queueRepository.enterIfAdmissionTokenActive(admitted.enter(), token)).isTrue();
        return queueRepository.findEntry(waiting.sessionId(), USER_ID).orElseThrow();
    }

    private void publishAndWait(UUID reservationId, String payload) throws Exception {
        var metadata = kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, reservationId.toString(), payload)
                .get(10, TimeUnit.SECONDS).getRecordMetadata();
        // DB 반영만 기다리면 Queue 종료 전에 단언할 수 있으므로 Consumer offset 커밋까지 대기
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                var offsets = admin.listConsumerGroupOffsets("ticketing-payment-group")
                        .partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
                var offset = offsets.get(new TopicPartition(metadata.topic(), metadata.partition()));
                assertThat(offset).isNotNull();
                assertThat(offset.offset()).isGreaterThan(metadata.offset());
            });
        }
    }

    private void insertSeat(UUID sessionId, UUID seatId, UUID holdId) {
        jdbcTemplate.update("""
                INSERT INTO p_schedule_seat (schedule_seat_id, event_session_id, venue_seat_id,
                    section, row_label, seat_number, seat_grade, price, seat_status, created_by, updated_by)
                VALUES (?, ?, ?, 'A', '1', '1', 'VIP', ?, 'HELD', ?, ?)
                """, seatId, sessionId, UUID.randomUUID(), AMOUNT, USER_ID, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO p_seat_hold (seat_hold_id, schedule_seat_id, user_id, hold_token, hold_status,
                    held_at, expires_at, idempotency_key, created_by, updated_by)
                VALUES (?, ?, ?, ?, 'HOLDING', CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP + INTERVAL '30 minutes', ?, ?, ?)
                """, holdId, seatId, USER_ID, UUID.randomUUID(), "hold-" + holdId, USER_ID, USER_ID);
    }
}
