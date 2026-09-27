package com.tikitaka.ticketing.seat.application.service;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.seat.domain.entity.SeatHold;
import com.tikitaka.ticketing.seat.exception.SeatErrorCode;
import com.tikitaka.ticketing.seat.infrastructure.repository.SeatHoldJpaRepository;
import com.tikitaka.ticketing.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@PostgresIntegrationTest
@TestPropertySource(properties = "reservation.kafka.consumer.auto-startup=false")
class SeatHoldExpirationRaceIntegrationTest {

    private static final long USER_ID = 94_000_001L;
    private static final long PRICE = 150_000L;
    private static final long SYNCHRONIZATION_TIMEOUT_SECONDS = 15L;

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatHoldJpaRepository seatHoldJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @Timeout(30)
    void 예매가_선점_잠금을_먼저_획득하면_뒤늦은_만료_처리는_RESERVED를_해제하지_않는다() throws Exception {
        Fixture fixture = insertExpiredHoldingFixture();
        CountDownLatch reservedWithLock = new CountDownLatch(1);
        CountDownLatch allowReservationCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> reservationFuture = executor.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    SeatHold seatHold = seatHoldJpaRepository.findByIdForUpdate(fixture.seatHoldId())
                            .orElseThrow();
                    // 만료 직전에 획득한 결제 처리 권한이 아직 커밋되지 않은 상황을 재현
                    seatHold.reserve(Instant.parse("2026-09-18T05:09:59Z"));
                    reservedWithLock.countDown();
                    awaitLatch(allowReservationCommit);
                });
                return null;
            });

            if (!reservedWithLock.await(SYNCHRONIZATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                if (reservationFuture.isDone()) {
                    reservationFuture.get();
                }
                throw new AssertionError("예매 트랜잭션이 제한 시간 안에 좌석 선점 잠금을 획득하지 못했습니다.");
            }

            Future<Void> expirationFuture = executor.submit(() -> {
                seatService.expireHold(fixture.seatHoldId());
                return null;
            });

            assertThatThrownBy(() -> expirationFuture.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            allowReservationCommit.countDown();
            reservationFuture.get(SYNCHRONIZATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            expirationFuture.get(SYNCHRONIZATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat(queryString(
                    "SELECT hold_status FROM p_seat_hold WHERE seat_hold_id = ?", fixture.seatHoldId()))
                    .isEqualTo("RESERVED");
            assertThat(queryString(
                    "SELECT seat_status FROM p_schedule_seat WHERE schedule_seat_id = ?", fixture.scheduleSeatId()))
                    .isEqualTo("HELD");
            assertThat(queryString(
                    "SELECT release_reason FROM p_seat_hold WHERE seat_hold_id = ?", fixture.seatHoldId()))
                    .isNull();
        } finally {
            allowReservationCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 만료_처리가_먼저_완료되면_예매는_결제_처리_권한을_획득하지_못한다() {
        Fixture fixture = insertExpiredHoldingFixture();

        seatService.expireHold(fixture.seatHoldId());

        BusinessException exception = catchThrowableOfType(
                () -> seatService.validateAndExtend(fixture.seatHoldId()), BusinessException.class);

        assertThat(exception.getErrorCode()).isEqualTo(SeatErrorCode.SEAT_HOLD_ALREADY_CLOSED);
        assertThat(queryString(
                "SELECT hold_status FROM p_seat_hold WHERE seat_hold_id = ?", fixture.seatHoldId()))
                .isEqualTo("RELEASED");
        assertThat(queryString(
                "SELECT release_reason FROM p_seat_hold WHERE seat_hold_id = ?", fixture.seatHoldId()))
                .isEqualTo("EXPIRED");
        assertThat(queryString(
                "SELECT seat_status FROM p_schedule_seat WHERE schedule_seat_id = ?", fixture.scheduleSeatId()))
                .isEqualTo("AVAILABLE");
    }

    private Fixture insertExpiredHoldingFixture() {
        UUID eventSessionId = UUID.randomUUID();
        UUID scheduleSeatId = UUID.randomUUID();
        UUID venueSeatId = UUID.randomUUID();
        UUID seatHoldId = UUID.randomUUID();
        UUID holdToken = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO p_schedule_seat (
                    schedule_seat_id, event_session_id, venue_seat_id,
                    section, row_label, seat_number, seat_grade, price, seat_status,
                    created_at, created_by, updated_at, updated_by
                ) VALUES (?, ?, ?, 'VIP', 'A', '1', 'VIP', ?, 'HELD',
                          CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?)
                """, scheduleSeatId, eventSessionId, venueSeatId, PRICE, USER_ID, USER_ID);

        jdbcTemplate.update("""
                INSERT INTO p_seat_hold (
                    seat_hold_id, schedule_seat_id, user_id, hold_token,
                    hold_status, held_at, expires_at,
                    created_at, created_by, updated_at, updated_by, idempotency_key
                ) VALUES (?, ?, ?, ?, 'HOLDING',
                          CURRENT_TIMESTAMP - INTERVAL '11 minutes',
                          CURRENT_TIMESTAMP - INTERVAL '1 minute',
                          CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, ?)
                """, seatHoldId, scheduleSeatId, USER_ID, holdToken,
                USER_ID, USER_ID, "s04-race-" + seatHoldId);

        return new Fixture(scheduleSeatId, seatHoldId);
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(SYNCHRONIZATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("트랜잭션 경합 동기화 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("트랜잭션 경합 동기화가 중단되었습니다.", exception);
        }
    }

    private String queryString(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, String.class, arguments);
    }

    private record Fixture(UUID scheduleSeatId, UUID seatHoldId) {
    }
}
