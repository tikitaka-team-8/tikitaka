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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.exception.CommonErrorCode;
import com.tikitaka.ticketing.queue.config.QueueProperties;
import com.tikitaka.ticketing.queue.domain.AdmissionToken;
import com.tikitaka.ticketing.queue.domain.AdmissionTokenStatus;
import com.tikitaka.ticketing.queue.domain.QueueEntry;
import com.tikitaka.ticketing.queue.domain.QueueStatus;
import com.tikitaka.ticketing.queue.exception.QueueErrorCode;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import feign.Response;
import java.net.SocketTimeoutException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

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
                new QueueProperties(Duration.ofMinutes(10), Duration.ofHours(1), 50, 50),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper()
        );
    }

    @Test
    void 활성_대기열이_있으면_Platform을_조회하지_않고_기존_엔트리를_반환한다() {
        QueueEntry existingEntry = waitingEntry(3L);
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(existingEntry));

        QueueEntry result = queueService.enterQueue(SESSION_ID, USER_ID);

        assertThat(result).isEqualTo(existingEntry);
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

        QueueEntry result = queueService.enterQueue(SESSION_ID, USER_ID);

        assertThat(result).isEqualTo(createdEntry);
    }

    @Test
    void 만료된_입장_권한은_새_WAITING_엔트리로_원자적으로_교체한다() {
        QueueEntry expiredEntry = waitingEntry(5L).admit(NOW.minus(Duration.ofMinutes(10))).expire(NOW);
        QueueEntry reenteredEntry = waitingEntry(6L);
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(expiredEntry));
        when(platformSalesStatusClient.getSalesStatus(SESSION_ID)).thenReturn(openSalesStatus());
        when(queueRepository.createWaitingEntryIfAbsent(
                eq(SESSION_ID), eq(USER_ID), eq(NOW), eq(NOW.plus(Duration.ofHours(2))), eq(Duration.ofHours(2))))
                .thenReturn(Optional.of(reenteredEntry));

        QueueEntry result = queueService.enterQueue(SESSION_ID, USER_ID);

        assertThat(result).isEqualTo(reenteredEntry);
        verify(queueRepository, never()).deleteEntry(SESSION_ID, USER_ID);
    }

    @Test
    void 판매_검증과_엔트리_생성에_동일한_시각을_사용한다() {
        Instant justBeforeSalesClose = NOW.minusMillis(1);
        Clock advancingClock = mock(Clock.class);
        when(advancingClock.instant()).thenReturn(justBeforeSalesClose, NOW);
        QueueService serviceWithAdvancingClock = new QueueService(
                queueRepository,
                platformSalesStatusClient,
                new QueueProperties(Duration.ofMinutes(10), Duration.ofHours(1), 50, 50),
                advancingClock,
                new ObjectMapper()
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
                "SCHEDULED",
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

        QueueEntry result = serviceWithAdvancingClock.enterQueue(SESSION_ID, USER_ID);

        assertThat(result).isEqualTo(createdEntry);
    }

    @Test
    void 동시_진입으로_생성이_실패하면_생성된_기존_엔트리를_반환한다() {
        QueueEntry concurrentEntry = waitingEntry(2L);
        when(queueRepository.findEntry(SESSION_ID, USER_ID))
                .thenReturn(Optional.empty(), Optional.of(concurrentEntry));
        when(platformSalesStatusClient.getSalesStatus(SESSION_ID)).thenReturn(openSalesStatus());
        when(queueRepository.createWaitingEntryIfAbsent(any(), anyLong(), any(), any(), any()))
                .thenReturn(Optional.empty());

        QueueEntry result = queueService.enterQueue(SESSION_ID, USER_ID);

        assertThat(result).isEqualTo(concurrentEntry);
    }

    @Test
    void 판매_시작_전_회차는_대기열_진입을_막는다() {
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.empty());
        when(platformSalesStatusClient.getSalesStatus(SESSION_ID)).thenReturn(new PlatformSalesStatus(
                SESSION_ID,
                "SCHEDULED",
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
                "SCHEDULED",
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
                "SCHEDULED",
                OffsetDateTime.parse("2026-09-01T09:00:00+09:00"),
                OffsetDateTime.parse("2026-09-01T11:00:00+09:00"),
                false
        ));

        assertQueueError(() -> queueService.enterQueue(SESSION_ID, USER_ID), QueueErrorCode.QUEUE_SESSION_NOT_OPEN);
        verify(queueRepository, never()).createWaitingEntryIfAbsent(any(), anyLong(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "CANCELED"})
    void 판매_시간_내여도_진행할_수_없는_회차_상태는_대기열_진입을_막는다(String sessionStatus) {
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.empty());
        when(platformSalesStatusClient.getSalesStatus(SESSION_ID)).thenReturn(new PlatformSalesStatus(
                SESSION_ID,
                sessionStatus,
                OffsetDateTime.parse("2026-09-01T09:00:00+09:00"),
                OffsetDateTime.parse("2026-09-01T11:00:00+09:00"),
                true
        ));

        assertQueueError(() -> queueService.enterQueue(SESSION_ID, USER_ID), QueueErrorCode.QUEUE_SESSION_NOT_OPEN);
        verify(queueRepository, never()).createWaitingEntryIfAbsent(any(), anyLong(), any(), any(), any());
    }

    @Test
    void Platform_5xx_호출_실패시_연동_실패_오류로_대기열_진입을_막는다() {
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.empty());
        when(platformSalesStatusClient.getSalesStatus(SESSION_ID)).thenThrow(platformUnavailable());

        assertQueueError(() -> queueService.enterQueue(SESSION_ID, USER_ID), CommonErrorCode.DOWNSTREAM_SERVICE_FAILURE);
        verify(queueRepository, never()).createWaitingEntryIfAbsent(any(), anyLong(), any(), any(), any());
    }

    @Test
    void Redis_연결_실패시_Queue_서비스_이용_불가_오류를_반환한다() {
        when(queueRepository.findEntry(SESSION_ID, USER_ID))
                .thenThrow(new RedisConnectionFailureException("Redis connection failed"));

        assertQueueError(() -> queueService.enterQueue(SESSION_ID, USER_ID), QueueErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        verify(platformSalesStatusClient, never()).getSalesStatus(any());
    }

    @Test
    void Platform_호출_Timeout시_연동_Timeout_오류로_대기열_진입을_막는다() {
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.empty());
        when(platformSalesStatusClient.getSalesStatus(SESSION_ID)).thenThrow(platformTimeout());

        assertQueueError(() -> queueService.enterQueue(SESSION_ID, USER_ID), CommonErrorCode.DOWNSTREAM_SERVICE_TIMEOUT);
        verify(queueRepository, never()).createWaitingEntryIfAbsent(any(), anyLong(), any(), any(), any());
    }

    @Test
    void Platform_도메인_오류는_code와_HTTP_status를_유지한다() {
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.empty());
        when(platformSalesStatusClient.getSalesStatus(SESSION_ID)).thenThrow(platformSessionNotFound());

        assertThatThrownBy(() -> queueService.enterQueue(SESSION_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode().getCode()).isEqualTo("E-007");
                    assertThat(businessException.getErrorCode().getStatus().value()).isEqualTo(404);
                });
        verify(queueRepository, never()).createWaitingEntryIfAbsent(any(), anyLong(), any(), any(), any());
    }

    @Test
    void 내_대기열_상태를_조회한다() {
        QueueEntry entry = waitingEntry(5L);
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(entry));

        QueueEntry result = queueService.getEntry(SESSION_ID, USER_ID);

        assertThat(result).isEqualTo(entry);
    }

    @Test
    void WAITING_상태_조회는_현재_순번을_반환한다() {
        QueueEntry entry = waitingEntry(5L);
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(entry));
        when(queueRepository.findWaitingPosition(SESSION_ID, USER_ID)).thenReturn(Optional.of(3L));

        QueueStatusResult result = queueService.getQueueStatus(SESSION_ID, USER_ID);

        assertThat(result.entry()).isEqualTo(entry);
        assertThat(result.position()).isEqualTo(3L);
        assertThat(result.admissionToken()).isNull();
        verify(queueRepository, never()).admitIfWaiting(any(), any(), any(), any());
    }

    @Test
    void ADMITTED_상태_조회는_입장_토큰을_반환한다() {
        QueueEntry entry = waitingEntry(5L).admit(NOW.minusSeconds(1));
        AdmissionToken token = activeAdmissionToken(USER_ID);
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(entry));
        when(queueRepository.findAdmissionTokenReference(SESSION_ID, USER_ID)).thenReturn(Optional.of(token.token()));
        when(queueRepository.findAdmissionToken(SESSION_ID, token.token())).thenReturn(Optional.of(token));

        QueueStatusResult result = queueService.getQueueStatus(SESSION_ID, USER_ID);

        assertThat(result.position()).isNull();
        assertThat(result.admissionToken()).isEqualTo(token);
        verify(queueRepository, never()).admitIfWaiting(any(), any(), any(), any());
    }

    @Test
    void EXPIRED_상태_조회는_순번과_입장_토큰을_반환하지_않는다() {
        QueueEntry expiredEntry = waitingEntry(5L).admit(NOW.minus(Duration.ofMinutes(10))).expire(NOW);
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(expiredEntry));

        QueueStatusResult result = queueService.getQueueStatus(SESSION_ID, USER_ID);

        assertThat(result.entry()).isEqualTo(expiredEntry);
        assertThat(result.position()).isNull();
        assertThat(result.admissionToken()).isNull();
        verify(queueRepository, never()).findWaitingPosition(SESSION_ID, USER_ID);
        verify(queueRepository, never()).findAdmissionTokenReference(SESSION_ID, USER_ID);
    }

    @Test
    void ADMITTED_사용자가_본인의_유효한_입장_토큰으로_좌석_영역에_진입한다() {
        QueueEntry admittedEntry = waitingEntry(5L).admit(NOW.minusSeconds(1));
        AdmissionToken token = activeAdmissionToken(USER_ID);
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(admittedEntry));
        when(queueRepository.findAdmissionToken(SESSION_ID, token.token())).thenReturn(Optional.of(token));
        when(queueRepository.enterIfAdmissionTokenActive(admittedEntry.enter(), token)).thenReturn(true);

        queueService.validateAndEnter(SESSION_ID, USER_ID, token.token());

        verify(queueRepository).enterIfAdmissionTokenActive(admittedEntry.enter(), token);
    }

    @Test
    void WAITING_사용자의_좌석_영역_접근을_거부한다() {
        AdmissionToken token = activeAdmissionToken(USER_ID);
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(waitingEntry(5L)));
        when(queueRepository.findAdmissionToken(SESSION_ID, token.token())).thenReturn(Optional.of(token));

        assertQueueError(
                () -> queueService.validateAndEnter(SESSION_ID, USER_ID, token.token()),
                QueueErrorCode.QUEUE_ACCESS_DENIED
        );
        verify(queueRepository, never()).enterIfAdmissionTokenActive(any(), any());
    }

    @Test
    void 대기열_참여_정보가_없으면_좌석_영역_접근을_거부한다() {
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.empty());

        assertQueueError(
                () -> queueService.validateAndEnter(SESSION_ID, USER_ID, "admission-token"),
                QueueErrorCode.QUEUE_ACCESS_DENIED
        );
        verify(queueRepository, never()).findAdmissionToken(any(), any());
    }

    @Test
    void 존재하지_않는_입장_토큰으로는_좌석_영역에_진입할_수_없다() {
        QueueEntry admittedEntry = waitingEntry(5L).admit(NOW.minusSeconds(1));
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(admittedEntry));
        when(queueRepository.findAdmissionToken(SESSION_ID, "admission-token")).thenReturn(Optional.empty());

        assertQueueError(
                () -> queueService.validateAndEnter(SESSION_ID, USER_ID, "admission-token"),
                QueueErrorCode.QUEUE_ACCESS_DENIED
        );
        verify(queueRepository, never()).enterIfAdmissionTokenActive(any(), any());
    }

    @Test
    void 다른_사용자에게_발급된_입장_토큰으로는_좌석_영역에_진입할_수_없다() {
        QueueEntry admittedEntry = waitingEntry(5L).admit(NOW.minusSeconds(1));
        AdmissionToken otherUsersToken = activeAdmissionToken(USER_ID + 1);
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(admittedEntry));
        when(queueRepository.findAdmissionToken(SESSION_ID, otherUsersToken.token()))
                .thenReturn(Optional.of(otherUsersToken));

        assertQueueError(
                () -> queueService.validateAndEnter(SESSION_ID, USER_ID, otherUsersToken.token()),
                QueueErrorCode.QUEUE_ACCESS_DENIED
        );
        verify(queueRepository, never()).enterIfAdmissionTokenActive(any(), any());
    }

    @Test
    void 다른_회차에_발급된_입장_토큰으로는_좌석_영역에_진입할_수_없다() {
        QueueEntry admittedEntry = waitingEntry(5L).admit(NOW.minusSeconds(1));
        AdmissionToken otherSessionToken = new AdmissionToken(
                "admission-token",
                UUID.randomUUID(),
                USER_ID,
                NOW.plus(Duration.ofMinutes(10)),
                AdmissionTokenStatus.ACTIVE
        );
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(admittedEntry));
        when(queueRepository.findAdmissionToken(SESSION_ID, otherSessionToken.token()))
                .thenReturn(Optional.of(otherSessionToken));

        assertQueueError(
                () -> queueService.validateAndEnter(SESSION_ID, USER_ID, otherSessionToken.token()),
                QueueErrorCode.QUEUE_ACCESS_DENIED
        );
        verify(queueRepository, never()).enterIfAdmissionTokenActive(any(), any());
    }

    @Test
    void 만료된_입장_토큰으로는_좌석_영역에_진입할_수_없다() {
        QueueEntry admittedEntry = waitingEntry(5L).admit(NOW.minusSeconds(1));
        AdmissionToken expiredToken = new AdmissionToken(
                "admission-token", SESSION_ID, USER_ID, NOW, AdmissionTokenStatus.ACTIVE
        );
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(admittedEntry));
        when(queueRepository.findAdmissionToken(SESSION_ID, expiredToken.token())).thenReturn(Optional.of(expiredToken));

        assertQueueError(
                () -> queueService.validateAndEnter(SESSION_ID, USER_ID, expiredToken.token()),
                QueueErrorCode.QUEUE_ACCESS_DENIED
        );
        verify(queueRepository, never()).enterIfAdmissionTokenActive(any(), any());
    }

    @Test
    void 동시에_입장한_요청이_이미_ENTERED로_전환된_경우는_성공으로_처리한다() {
        QueueEntry admittedEntry = waitingEntry(5L).admit(NOW.minusSeconds(1));
        AdmissionToken activeToken = activeAdmissionToken(USER_ID);
        AdmissionToken usedToken = activeToken.use();
        when(queueRepository.findEntry(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(admittedEntry), Optional.of(admittedEntry.enter()));
        when(queueRepository.findAdmissionToken(SESSION_ID, activeToken.token()))
                .thenReturn(Optional.of(activeToken), Optional.of(usedToken));
        when(queueRepository.enterIfAdmissionTokenActive(admittedEntry.enter(), activeToken)).thenReturn(false);

        queueService.validateAndEnter(SESSION_ID, USER_ID, activeToken.token());
    }

    @Test
    void ENTERED_사용자의_후속_좌석_요청을_허용한다() {
        QueueEntry enteredEntry = waitingEntry(5L).admit(NOW.minusSeconds(1)).enter();
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(enteredEntry));

        queueService.validateEntered(SESSION_ID, USER_ID);
    }

    @Test
    void 아직_ENTERED가_아닌_사용자의_후속_좌석_요청을_거부한다() {
        QueueEntry admittedEntry = waitingEntry(5L).admit(NOW.minusSeconds(1));
        when(queueRepository.findEntry(SESSION_ID, USER_ID)).thenReturn(Optional.of(admittedEntry));

        assertQueueError(
                () -> queueService.validateEntered(SESSION_ID, USER_ID),
                QueueErrorCode.QUEUE_ACCESS_DENIED
        );
    }

    @Test
    void ENTERED_검증_중_Redis_장애가_발생하면_좌석_접근을_차단한다() {
        when(queueRepository.findEntry(SESSION_ID, USER_ID))
                .thenThrow(new RedisConnectionFailureException("Redis connection failed"));

        assertQueueError(
                () -> queueService.validateEntered(SESSION_ID, USER_ID),
                QueueErrorCode.QUEUE_SERVICE_UNAVAILABLE
        );
    }

    private QueueEntry waitingEntry(long sequence) {
        return QueueEntry.waiting(SESSION_ID, USER_ID, sequence, NOW, NOW.plus(Duration.ofHours(2)));
    }

    private AdmissionToken activeAdmissionToken(long userId) {
        return new AdmissionToken(
                "admission-token",
                SESSION_ID,
                userId,
                NOW.plus(Duration.ofMinutes(10)),
                AdmissionTokenStatus.ACTIVE
        );
    }

    private PlatformSalesStatus openSalesStatus() {
        return new PlatformSalesStatus(
                SESSION_ID,
                "SCHEDULED",
                OffsetDateTime.parse("2026-09-01T09:00:00+09:00"),
                OffsetDateTime.parse("2026-09-01T11:00:00+09:00"),
                true
        );
    }

    private FeignException platformUnavailable() {
        return platformException(503, "Service Unavailable");
    }

    private FeignException platformSessionNotFound() {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://localhost:8081/api/v1/internal/event-sessions/" + SESSION_ID + "/sales-status",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        Response response = Response.builder()
                .status(404)
                .reason("Not Found")
                .request(request)
                .body("{\"code\":\"E-007\",\"status\":404,\"message\":\"공연 회차를 찾을 수 없습니다.\"}".getBytes(StandardCharsets.UTF_8))
                .build();
        return FeignException.errorStatus("getSalesStatus", response);
    }

    private RetryableException platformTimeout() {
        return new RetryableException(
                -1,
                "Read timed out",
                Request.HttpMethod.GET,
                new SocketTimeoutException("Read timed out"),
                (Long) null,
                platformRequest()
        );
    }

    private FeignException platformException(int status, String reason) {
        Response response = Response.builder()
                .status(status)
                .reason(reason)
                .request(platformRequest())
                .build();
        return FeignException.errorStatus("getSalesStatus", response);
    }

    private Request platformRequest() {
        return Request.create(
                Request.HttpMethod.GET,
                "http://localhost:8081/api/v1/internal/event-sessions/" + SESSION_ID + "/sales-status",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null
        );
    }

    private void assertQueueError(ThrowingCallable action, com.tikitaka.ticketing.global.exception.ErrorCode expectedErrorCode) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expectedErrorCode);
    }
}
