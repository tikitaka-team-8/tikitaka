package com.tikitaka.ticketing.queue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.queue.config.QueueProperties;
import com.tikitaka.ticketing.queue.domain.QueueEntry;
import com.tikitaka.ticketing.queue.domain.QueueStatus;
import com.tikitaka.ticketing.queue.exception.QueueErrorCode;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {
    private static final UUID SESSION_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-09-01T01:00:00Z");

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private PlatformSalesStatusClient platformSalesStatusClient;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(
                queueRepository,
                platformSalesStatusClient,
                new QueueProperties(Duration.ofMinutes(10), Duration.ofHours(1)),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void 활성_대기열이_있으면_Platform을_조회하지_않고_기존_엔트리를_반환한다() {
        QueueEntry existingEntry = waitingEntry(3L);
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(existingEntry));

        QueueCommandResult result = queueService.enterQueue(SESSION_ID, USER_ID);

        assertThat(result.entry()).isEqualTo(existingEntry);
        assertThat(result.existingEntry()).isTrue();
        verify(platformSalesStatusClient, never()).getSalesStatus(any());
    }

    @Test
    void 판매_가능한_회차에_WAITING_엔트리를_생성한다() {
        QueueEntry createdEntry = waitingEntry(11L);
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.empty());
        when(platformSalesStatusClient.getSalesStatus(SESSION_ID)).thenReturn(openSalesStatus());
        when(queueRepository.createWaitingEntryIfAbsent(
                eq(SESSION_ID), eq(USER_ID), eq(NOW), eq(NOW.plus(Duration.ofHours(2))), eq(Duration.ofHours(2))))
                .thenReturn(Optional.of(createdEntry));

        QueueCommandResult result = queueService.enterQueue(SESSION_ID, USER_ID);

        assertThat(result.entry()).isEqualTo(createdEntry);
        assertThat(result.existingEntry()).isFalse();
    }

    @Test
    void 판매_검증과_엔트리_생성에_동일한_시각을_사용한다() {
        Instant justBeforeSalesClose = NOW.minusMillis(1);
        Clock advancingClock = mock(Clock.class);
        when(advancingClock.instant()).thenReturn(justBeforeSalesClose, NOW);
        QueueService serviceWithAdvancingClock = new QueueService(
                queueRepository,
                platformSalesStatusClient,
                new QueueProperties(Duration.ofMinutes(10), Duration.ofHours(1)),
                advancingClock
        );
        QueueEntry createdEntry = QueueEntry.waiting(
                SESSION_ID,
                USER_ID,
                11L,
                justBeforeSalesClose,
                NOW.plus(Duration.ofHours(1))
        );
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.empty());
        when(platformSalesStatusClient.getSalesStatus(SESSION_ID)).thenReturn(new PlatformSalesStatus(
                SESSION_ID,
                "ON_SALE",
                OffsetDateTime.parse("2026-09-01T09:00:00+09:00"),
                OffsetDateTime.parse("2026-09-01T10:00:00+09:00"),
                true
        ));
        when(queueRepository.createWaitingEntryIfAbsent(
                eq(SESSION_ID),
                eq(USER_ID),
                eq(justBeforeSalesClose),
                eq(NOW.plus(Duration.ofHours(1))),
                eq(Duration.ofHours(1).plusMillis(1))
        )).thenReturn(Optional.of(createdEntry));

        QueueCommandResult result = serviceWithAdvancingClock.enterQueue(SESSION_ID, USER_ID);

        assertThat(result.entry()).isEqualTo(createdEntry);
    }

    @Test
    void 동시_진입으로_생성이_실패하면_생성된_기존_엔트리를_반환한다() {
        QueueEntry concurrentEntry = waitingEntry(2L);
        when(queueRepository.findEntry(SESSION_ID, USER_ID))
                .thenReturn(Optional.empty(), Optional.of(concurrentEntry));
        when(platformSalesStatusClient.getSalesStatus(SESSION_ID)).thenReturn(openSalesStatus());
        when(queueRepository.createWaitingEntryIfAbsent(any(), anyLong(), any(), any(), any()))
                .thenReturn(Optional.empty());

        QueueCommandResult result = queueService.enterQueue(SESSION_ID, USER_ID);

        assertThat(result.entry()).isEqualTo(concurrentEntry);
        assertThat(result.existingEntry()).isTrue();
    }

    @Test
    void 판매_시작_전_회차는_대기열_진입을_막는다() {
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.empty());
        when(platformSalesStatusClient.getSalesStatus(SESSION_ID)).thenReturn(new PlatformSalesStatus(
                SESSION_ID,
                "ON_SALE",
                OffsetDateTime.parse("2026-09-01T11:00:00+09:00"),
                OffsetDateTime.parse("2026-09-01T12:00:00+09:00"),
                true
        ));

        assertQueueError(() -> queueService.enterQueue(SESSION_ID, USER_ID), QueueErrorCode.QUEUE_SESSION_NOT_OPEN);
        verify(queueRepository, never()).createWaitingEntryIfAbsent(any(), anyLong(), any(), any(), any());
    }

    @Test
    void 판매_종료_시각에_도달한_회차는_대기열_진입을_막는다() {
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.empty());
        when(platformSalesStatusClient.getSalesStatus(SESSION_ID)).thenReturn(new PlatformSalesStatus(
                SESSION_ID,
                "ON_SALE",
                OffsetDateTime.parse("2026-09-01T09:00:00+09:00"),
                OffsetDateTime.parse("2026-09-01T10:00:00+09:00"),
                true
        ));

        assertQueueError(() -> queueService.enterQueue(SESSION_ID, USER_ID), QueueErrorCode.QUEUE_SESSION_NOT_OPEN);
        verify(queueRepository, never()).createWaitingEntryIfAbsent(any(), anyLong(), any(), any(), any());
    }

    @Test
    void Queue가_비활성화된_회차는_대기열_진입을_막는다() {
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.empty());
        when(platformSalesStatusClient.getSalesStatus(SESSION_ID)).thenReturn(new PlatformSalesStatus(
                SESSION_ID,
                "ON_SALE",
                OffsetDateTime.parse("2026-09-01T09:00:00+09:00"),
                OffsetDateTime.parse("2026-09-01T11:00:00+09:00"),
                false
        ));

        assertQueueError(() -> queueService.enterQueue(SESSION_ID, USER_ID), QueueErrorCode.QUEUE_SESSION_NOT_OPEN);
        verify(queueRepository, never()).createWaitingEntryIfAbsent(any(), anyLong(), any(), any(), any());
    }

    @Test
    void Platform_호출_실패시_fail_closed로_대기열_진입을_막는다() {
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.empty());
        when(platformSalesStatusClient.getSalesStatus(SESSION_ID)).thenThrow(platformUnavailable());

        assertQueueError(() -> queueService.enterQueue(SESSION_ID, USER_ID), QueueErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        verify(queueRepository, never()).createWaitingEntryIfAbsent(any(), anyLong(), any(), any(), any());
    }

    @Test
    void Platform에서_회차를_찾지_못하면_회차_없음_오류를_반환한다() {
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.empty());
        when(platformSalesStatusClient.getSalesStatus(SESSION_ID)).thenThrow(platformSessionNotFound());

        assertQueueError(() -> queueService.enterQueue(SESSION_ID, USER_ID), QueueErrorCode.QUEUE_SESSION_NOT_FOUND);
        verify(queueRepository, never()).createWaitingEntryIfAbsent(any(), anyLong(), any(), any(), any());
    }

    @Test
    void 내_대기열_상태를_조회한다() {
        QueueEntry entry = waitingEntry(5L);
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(entry));

        QueueEntry result = queueService.getEntry(SESSION_ID, USER_ID);

        assertThat(result).isEqualTo(entry);
    }

    private QueueEntry waitingEntry(long sequence) {
        return QueueEntry.waiting(SESSION_ID, USER_ID, sequence, NOW, NOW.plus(Duration.ofHours(2)));
    }

    private PlatformSalesStatus openSalesStatus() {
        return new PlatformSalesStatus(
                SESSION_ID,
                "ON_SALE",
                OffsetDateTime.parse("2026-09-01T09:00:00+09:00"),
                OffsetDateTime.parse("2026-09-01T11:00:00+09:00"),
                true
        );
    }

    private FeignException platformUnavailable() {
        return platformException(503, "Service Unavailable");
    }

    private FeignException platformSessionNotFound() {
        return platformException(404, "Not Found");
    }

    private FeignException platformException(int status, String reason) {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://localhost:8081/api/v1/internal/event-sessions/" + SESSION_ID + "/sales-status",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        Response response = Response.builder()
                .status(status)
                .reason(reason)
                .request(request)
                .build();
        return FeignException.errorStatus("getSalesStatus", response);
    }

    private void assertQueueError(ThrowingCallable action, QueueErrorCode expectedErrorCode) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expectedErrorCode);
    }
}
