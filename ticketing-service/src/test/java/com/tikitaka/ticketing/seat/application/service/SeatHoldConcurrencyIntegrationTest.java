package com.tikitaka.ticketing.seat.application.service;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.queue.application.QueueService;
import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import com.tikitaka.ticketing.seat.domain.entity.SeatHold;
import com.tikitaka.ticketing.seat.domain.enums.HoldStatus;
import com.tikitaka.ticketing.seat.domain.enums.SeatStatus;
import com.tikitaka.ticketing.seat.exception.SeatErrorCode;
import com.tikitaka.ticketing.seat.infrastructure.repository.ScheduleSeatJpaRepository;
import com.tikitaka.ticketing.seat.infrastructure.repository.SeatHoldJpaRepository;
import com.tikitaka.ticketing.seat.presentation.dto.response.SeatHoldResponse;
import com.tikitaka.ticketing.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;

/**
 * 실제 PostgreSQL(Testcontainers)로 여러 스레드가 동시에 좌석 선점을 요청했을 때
 * 비관적 락(findByIdForUpdate) + 파셜 유니크 인덱스(uq_seat_hold_active_schedule_seat)가
 * 실제로 동시성을 제어하는지 검증한다.
 *
 * SeatServiceTest(Mockito 기반 단위 테스트)는 요청이 순서대로 처리된다고 가정하므로,
 * "완전히 동시에" 여러 요청이 들어오는 상황은 이 통합 테스트로만 검증할 수 있다.
 */
@PostgresIntegrationTest
class SeatHoldConcurrencyIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(SeatHoldConcurrencyIntegrationTest.class);

    @Autowired
    private SeatService seatService;

    @Autowired
    private ScheduleSeatJpaRepository scheduleSeatJpaRepository;

    @Autowired
    private SeatHoldJpaRepository seatHoldJpaRepository;

    // QueueService가 QueueAdmissionValidator를 구현하는 유일한 빈이라, 인터페이스 타입을 그대로
    // @MockitoBean으로 목킹하면 QueueController가 의존하는 실제 QueueService 빈까지 통째로
    // 대체되어 컨텍스트 로딩이 깨진다. 실제 빈을 스파이로 감싸고 필요한 메서드만 no-op으로 스텁한다.
    @MockitoSpyBean
    private QueueService queueService;

    private UUID eventSessionId;
    private UUID scheduleSeatId;

    @BeforeEach
    void setUp() {
        eventSessionId = UUID.randomUUID();

        doNothing()
                .when(queueService)
                .validateEntered(any(UUID.class), anyLong());

        ScheduleSeat seat = ScheduleSeat.create(
                eventSessionId, UUID.randomUUID(), "A", "1", "1", "VIP", 10_000L, 0L
        );
        scheduleSeatId = scheduleSeatJpaRepository.saveAndFlush(seat).getScheduleSeatId();
    }

    @Test
    void 같은_좌석에_대한_100건의_동시_선점_요청_중_단_1건만_성공한다() throws InterruptedException {
        int requestCount = 100;
        // ready/start/done 3단 래치 패턴에서는 풀 크기가 requestCount보다 작으면
        // 큐에 밀린 작업이 readyLatch를 채우지 못해 데드락에 빠진다. 항상 requestCount 이상으로 맞춘다.
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger seatUnavailableCount = new AtomicInteger();
        AtomicInteger unexpectedFailureCount = new AtomicInteger();

        try {
            IntStream.range(0, requestCount).forEach(i -> {
                long userId = i + 1L;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {

                        startLatch.await();
                        System.out.println(userId);
                        seatService.holdSeat(eventSessionId, scheduleSeatId, userId, "idem-" + userId);

                        successCount.incrementAndGet();
                    } catch (BusinessException e) {

                        if (e.getErrorCode() == SeatErrorCode.SEAT_UNAVAILABLE) {
                            seatUnavailableCount.incrementAndGet();
                        } else {
                            unexpectedFailureCount.incrementAndGet();
                            log.error("예상하지 못한 BusinessException 발생", e);
                        }
                    } catch (Exception e) {
                        unexpectedFailureCount.incrementAndGet();
                        log.error("예상하지 못한 예외 발생", e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            });

            readyLatch.await();
            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);

            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(seatUnavailableCount.get()).isEqualTo(requestCount - 1);
            assertThat(unexpectedFailureCount.get()).isZero();

        } finally {
            executor.shutdownNow();
        }

        long holdingCount = seatHoldJpaRepository.findAll().stream()
                .filter(hold -> hold.getScheduleSeatId().equals(scheduleSeatId))
                .filter(hold -> hold.getHoldStatus() == HoldStatus.HOLDING)
                .count();
        assertThat(holdingCount).isEqualTo(1);
        List<SeatHold> holds = seatHoldJpaRepository.findAll().stream()
                .filter(hold -> hold.getScheduleSeatId().equals(scheduleSeatId))
                .toList();


        ScheduleSeat seat = scheduleSeatJpaRepository.findById(scheduleSeatId).orElseThrow();

        assertThat(seat.getSeatStatus()).isEqualTo(SeatStatus.HELD);

        log.info("========== 동일 좌석 동시 선점 테스트 결과 ==========");
        log.info("성공 = {}", successCount.get());
        log.info("좌석 선점 실패 = {}", seatUnavailableCount.get());
        log.info("예상하지 못한 실패 = {}", unexpectedFailureCount.get());
        log.info("완료되지 않은 작업 = {}", doneLatch.getCount());

        log.info("========== SeatHold DB 조회 ==========");
        log.info("SeatHold 개수 = {}", holds.size());

        holds.forEach(hold ->
                log.info(
                        "SeatHold: id={}, userId={}, seatId={}, status={}, idempotencyKey={}",
                        hold.getSeatHoldId(),
                        hold.getUserId(),
                        hold.getScheduleSeatId(),
                        hold.getHoldStatus(),
                        hold.getIdempotencyKey()
                )
        );
        log.info("==========================================");
    }


    @Test
    void 동일_Idempotency_Key로_동시에_요청해도_SeatHold는_1건만_생성된다() throws InterruptedException {
        long userId = 1L;
        String idempotencyKey = "same-idempotency-key";
        int requestCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);

        List<UUID> successfulHoldIds = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger rejectedCount = new AtomicInteger();

        try {
            IntStream.range(0, requestCount).forEach(i -> executor.submit(() -> {
                String threadName = Thread.currentThread().getName();

                readyLatch.countDown();
                try {
                    startLatch.await();
                    SeatHoldResponse response =
                            seatService.holdSeat(eventSessionId, scheduleSeatId, userId, idempotencyKey);
                    successfulHoldIds.add(response.seatHoldId());
                    log.info(
                            "[SUCCESS] thread={}, userId={}, seatHoldId={}, idempotencyKey={}",
                            threadName,
                            userId,
                            response.seatHoldId(),
                            idempotencyKey
                    );
                } catch (BusinessException e) {
                    rejectedCount.incrementAndGet();
                    log.info(
                            "[REJECTED] thread={}, userId={}, errorCode={}, message={}",
                            threadName,
                            userId,
                            e.getErrorCode(),
                            e.getMessage()
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error(
                            "[INTERRUPTED] thread={}, userId={}",
                            threadName,
                            userId,
                            e
                    );
                } catch (Exception e) {
                    log.error(
                            "[UNEXPECTED ERROR] thread={}, userId={}",
                            threadName,
                            userId,
                            e
                    );
                } finally {
                    doneLatch.countDown();
                }
            }));

            readyLatch.await();
            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
        } finally {
            executor.shutdownNow();
        }

        // 핵심 불변식: 성공/실패 여부와 무관하게 같은 (userId, idempotencyKey)로는
        // 절대 2건 이상의 SeatHold가 생성되면 안 된다. (uq_seat_hold_user_idempotency)
        List<SeatHold> persistedHolds = seatHoldJpaRepository.findAll().stream()
                .filter(hold -> hold.getUserId().equals(userId) && hold.getIdempotencyKey().equals(idempotencyKey))
                .toList();
        assertThat(persistedHolds).hasSize(1);

        // 성공 응답이 있었다면, 전부 같은 선점 건을 가리켜야 한다(멱등).
        Set<UUID> distinctSuccessIds = successfulHoldIds.stream().collect(Collectors.toSet());
        assertThat(distinctSuccessIds.size()).isLessThanOrEqualTo(1);

        // 참고(알려진 동작): 완전 동시 요청에서는 두 번째 이후 요청이 DataIntegrityViolationException
        // 재조회 경로에 도달하기 전에 좌석 상태 체크(SEAT_UNAVAILABLE)에서 먼저 걸릴 수 있다.
        // 즉 "동시" 중복 요청에서는 일부가 멱등 응답 대신 에러를 받을 수 있다 - 순차 재시도에서는 정상 동작한다.
        log.info(
                "========== 최종 결과 =========="
        );
        persistedHolds.forEach(hold ->
                log.info(
                        "[DB HOLD] seatHoldId={}, userId={}, scheduleSeatId={}, status={}, idempotencyKey={}",
                        hold.getSeatHoldId(),
                        hold.getUserId(),
                        hold.getScheduleSeatId(),
                        hold.getHoldStatus(),
                        hold.getIdempotencyKey()
                )
        );
        log.info(
                "성공={}건, 거절={}건, 서로 다른 SeatHoldId={}개",
                successfulHoldIds.size(),
                rejectedCount.get(),
                distinctSuccessIds.size()
        );
    }

}
