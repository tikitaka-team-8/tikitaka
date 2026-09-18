package com.tikitaka.ticketing.reservation.application;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.exception.CommonErrorCode;
import com.tikitaka.ticketing.reservation.application.command.CreateReservationCommand;
import com.tikitaka.ticketing.reservation.application.result.CreateReservationResult;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import com.tikitaka.ticketing.reservation.domain.model.PaymentCreationInfo;
import com.tikitaka.ticketing.reservation.domain.model.ReservationEventSessionInfo;
import com.tikitaka.ticketing.reservation.domain.port.EventSessionQueryPort;
import com.tikitaka.ticketing.reservation.domain.port.PaymentCreationPort;
import com.tikitaka.ticketing.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@PostgresIntegrationTest
@TestPropertySource(properties = "reservation.kafka.consumer.auto-startup=false")
class ReservationResponseLossRecoveryIntegrationTest {
    private static final long USER_ID = 95_000_001L;
    private static final long AMOUNT = 150_000L;
    private static final String IDEMPOTENCY_KEY = "s05-reservation-response-loss-integration";
    private static final UUID EVENT_ID = UUID.fromString("25050000-0000-0000-0000-000000000001");
    private static final UUID EVENT_SESSION_ID = UUID.fromString("35050000-0000-0000-0000-000000000001");
    private static final UUID VENUE_SEAT_ID = UUID.fromString("45050000-0000-0000-0000-000000000001");
    private static final UUID SCHEDULE_SEAT_ID = UUID.fromString("55050000-0000-0000-0000-000000000001");
    private static final UUID SEAT_HOLD_ID = UUID.fromString("65050000-0000-0000-0000-000000000001");
    private static final UUID PAYMENT_ID = UUID.fromString("75050000-0000-0000-0000-000000000001");

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EventSessionQueryPort eventSessionQueryPort;

    @MockitoBean
    private PaymentCreationPort paymentCreationPort;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        insertSeatHoldFixture();
        given(eventSessionQueryPort.getReservationInfo(EVENT_SESSION_ID))
                .willReturn(new ReservationEventSessionInfo(
                        EVENT_SESSION_ID,
                        EVENT_ID,
                        "S05 응답 유실 테스트 공연",
                        OffsetDateTime.parse("2026-09-30T19:00:00+09:00")
                ));
    }

    @Test
    void Payment_응답이_유실되어도_예매_의도를_커밋하고_동일_요청으로_복구한다() {
        // given
        CreateReservationCommand command = new CreateReservationCommand(
                USER_ID, "USER", IDEMPOTENCY_KEY, List.of(SEAT_HOLD_ID));
        given(paymentCreationPort.createPayment(any(), any(), any(), any()))
                .willThrow(new BusinessException(CommonErrorCode.DOWNSTREAM_SERVICE_TIMEOUT));

        // when: Payment는 처리했지만 Ticketing이 응답을 받지 못한 상황을 재현
        BusinessException firstException = catchThrowableOfType(
                () -> reservationService.createReservation(command), BusinessException.class);

        // then: 외부 호출 실패와 무관하게 복구 기준점이 먼저 커밋됨
        assertThat(firstException.getErrorCode()).isEqualTo(CommonErrorCode.DOWNSTREAM_SERVICE_TIMEOUT);
        assertThat(queryLong("SELECT COUNT(*) FROM p_reservation WHERE idempotency_key = ?", IDEMPOTENCY_KEY))
                .isEqualTo(1L);
        UUID reservationId = queryUuid(
                "SELECT reservation_id FROM p_reservation WHERE idempotency_key = ?", IDEMPOTENCY_KEY);
        assertThat(queryString(
                "SELECT reservation_status FROM p_reservation WHERE reservation_id = ?", reservationId))
                .isEqualTo("PAYMENT_PENDING");
        assertThat(queryUuid(
                "SELECT payment_id FROM p_reservation WHERE reservation_id = ?", reservationId))
                .isNull();
        assertThat(queryString(
                "SELECT hold_status FROM p_seat_hold WHERE seat_hold_id = ?", SEAT_HOLD_ID))
                .isEqualTo("RESERVED");

        // when: 같은 요청을 재전송하면 Payment의 기존 멱등 결과를 받는 상황을 재현
        willReturn(new PaymentCreationInfo(
                        PAYMENT_ID,
                        reservationId,
                        "PAY-S05-INTEGRATION",
                        AMOUNT,
                        "READY",
                        OffsetDateTime.now(ZoneOffset.UTC)
                ))
                .given(paymentCreationPort)
                .createPayment(reservationId, USER_ID, AMOUNT, IDEMPOTENCY_KEY);
        CreateReservationResult retryResult = reservationService.createReservation(command);

        // then: 추가 생성 없이 기존 예매가 결제 처리 중 상태로 복구됨
        assertThat(retryResult.isCreated()).isFalse();
        assertThat(retryResult.getReservationId()).isEqualTo(reservationId);
        assertThat(retryResult.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(retryResult.getReservationStatus()).isEqualTo(ReservationStatus.PAYMENT_PROCESSING);
        assertThat(queryLong("SELECT COUNT(*) FROM p_reservation WHERE idempotency_key = ?", IDEMPOTENCY_KEY))
                .isEqualTo(1L);
        assertThat(queryUuid(
                "SELECT payment_id FROM p_reservation WHERE reservation_id = ?", reservationId))
                .isEqualTo(PAYMENT_ID);
        assertThat(queryString(
                "SELECT reservation_status FROM p_reservation WHERE reservation_id = ?", reservationId))
                .isEqualTo("PAYMENT_PROCESSING");
        verify(paymentCreationPort, times(2)).createPayment(
                reservationId, USER_ID, AMOUNT, IDEMPOTENCY_KEY);
    }

    private void insertSeatHoldFixture() {
        jdbcTemplate.update("""
                INSERT INTO p_schedule_seat (
                    schedule_seat_id, event_session_id, venue_seat_id,
                    section, row_label, seat_number, seat_grade, price, seat_status,
                    created_by, updated_by
                ) VALUES (?, ?, ?, 'A', '1', '1', 'VIP', ?, 'HELD', ?, ?)
                """, SCHEDULE_SEAT_ID, EVENT_SESSION_ID, VENUE_SEAT_ID, AMOUNT, USER_ID, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO p_seat_hold (
                    seat_hold_id, schedule_seat_id, user_id, hold_token, hold_status,
                    held_at, expires_at, idempotency_key, created_by, updated_by
                ) VALUES (?, ?, ?, ?, 'HOLDING', CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP + INTERVAL '30 minutes', ?, ?, ?)
                """, SEAT_HOLD_ID, SCHEDULE_SEAT_ID, USER_ID, UUID.randomUUID(),
                "s05-seat-hold-integration", USER_ID, USER_ID);
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM p_reservation_inbox");
        jdbcTemplate.update("DELETE FROM p_reservation_outbox");
        jdbcTemplate.update("DELETE FROM p_reservation_seats");
        jdbcTemplate.update("DELETE FROM p_reservation");
        jdbcTemplate.update("DELETE FROM p_seat_hold");
        jdbcTemplate.update("DELETE FROM p_schedule_seat");
    }

    private long queryLong(String sql, Object... args) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
        return result == null ? 0L : result;
    }

    private UUID queryUuid(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, UUID.class, args);
    }

    private String queryString(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }
}
