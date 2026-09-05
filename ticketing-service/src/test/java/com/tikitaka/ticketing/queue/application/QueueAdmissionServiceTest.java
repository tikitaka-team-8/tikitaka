package com.tikitaka.ticketing.queue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tikitaka.ticketing.queue.config.QueueProperties;
import com.tikitaka.ticketing.queue.domain.AdmissionToken;
import com.tikitaka.ticketing.queue.domain.AdmissionTokenStatus;
import com.tikitaka.ticketing.queue.domain.QueueEntry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueueAdmissionServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-03T01:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Mock
    private QueueRepository queueRepository;

    private QueueAdmissionService queueAdmissionService;

    @BeforeEach
    void setUp() {
        queueAdmissionService = new QueueAdmissionService(
                queueRepository,
                new QueueProperties(Duration.ofMinutes(10), Duration.ofHours(1), 50, 50),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void 회차별_선두_사용자_최대_batchSize명을_입장_허용한다() {
        QueueEntry first = waitingEntry(100L, 1L);
        QueueEntry second = waitingEntry(200L, 2L);
        when(queueRepository.findWaitingSessionIds()).thenReturn(Set.of(SESSION_ID));
        when(queueRepository.findWaitingEntries(SESSION_ID, 50)).thenReturn(List.of(first, second));

        queueAdmissionService.admitWaitingUsers();

        verify(queueRepository).admitIfWaiting(
                eq(first.admit(NOW)),
                argThat(token -> validTokenFor(token, first.userId())),
                eq(Duration.ofHours(2)),
                eq(Duration.ofMinutes(10))
        );
        verify(queueRepository).admitIfWaiting(
                eq(second.admit(NOW)),
                argThat(token -> validTokenFor(token, second.userId())),
                eq(Duration.ofHours(2)),
                eq(Duration.ofMinutes(10))
        );
    }

    @Test
    void 대기_사용자가_없는_registry_회차는_제거한다() {
        when(queueRepository.findWaitingSessionIds()).thenReturn(Set.of(SESSION_ID));
        when(queueRepository.findWaitingEntries(SESSION_ID, 50)).thenReturn(List.of());

        queueAdmissionService.admitWaitingUsers();

        verify(queueRepository).removeWaitingSessionIfEmpty(SESSION_ID);
    }

    @Test
    void 여러_회차를_각각_batchSize명까지_독립적으로_처리한다() {
        UUID otherSessionId = UUID.randomUUID();
        QueueEntry first = waitingEntry(100L, 1L);
        QueueEntry second = QueueEntry.waiting(
                otherSessionId, 200L, 1L, NOW.minusSeconds(1), NOW.plus(Duration.ofHours(2))
        );
        when(queueRepository.findWaitingSessionIds()).thenReturn(Set.of(SESSION_ID, otherSessionId));
        when(queueRepository.findWaitingEntries(SESSION_ID, 50)).thenReturn(List.of(first));
        when(queueRepository.findWaitingEntries(otherSessionId, 50)).thenReturn(List.of(second));

        queueAdmissionService.admitWaitingUsers();

        verify(queueRepository).findWaitingEntries(SESSION_ID, 50);
        verify(queueRepository).findWaitingEntries(otherSessionId, 50);
        verify(queueRepository).admitIfWaiting(eq(first.admit(NOW)), any(), eq(Duration.ofHours(2)), eq(Duration.ofMinutes(10)));
        verify(queueRepository).admitIfWaiting(eq(second.admit(NOW)), any(), eq(Duration.ofHours(2)), eq(Duration.ofMinutes(10)));
    }

    @Test
    void 한_사용자_처리_실패가_같은_회차의_다른_사용자_입장을_막지_않는다() {
        QueueEntry failedEntry = waitingEntry(100L, 1L);
        QueueEntry succeedingEntry = waitingEntry(200L, 2L);
        when(queueRepository.findWaitingSessionIds()).thenReturn(Set.of(SESSION_ID));
        when(queueRepository.findWaitingEntries(SESSION_ID, 50)).thenReturn(List.of(failedEntry, succeedingEntry));
        when(queueRepository.admitIfWaiting(eq(failedEntry.admit(NOW)), any(), any(), any()))
                .thenThrow(new IllegalStateException("invalid entry"));

        queueAdmissionService.admitWaitingUsers();

        verify(queueRepository).admitIfWaiting(
                eq(succeedingEntry.admit(NOW)),
                argThat(token -> validTokenFor(token, succeedingEntry.userId())),
                eq(Duration.ofHours(2)),
                eq(Duration.ofMinutes(10))
        );
    }

    @Test
    void 만료된_ADMITTED_사용자를_EXPIRED로_전환한다() {
        QueueEntry admittedEntry = waitingEntry(100L, 1L).admit(NOW.minus(Duration.ofMinutes(10)));
        when(queueRepository.findActiveSessionIds()).thenReturn(Set.of(SESSION_ID));
        when(queueRepository.findExpiredAdmittedEntries(SESSION_ID, NOW, 50)).thenReturn(List.of(admittedEntry));

        queueAdmissionService.expireAdmittedUsers();

        verify(queueRepository).expireIfAdmitted(admittedEntry.expire(NOW), NOW);
    }

    @Test
    void 만료_대상이_없는_회차는_active_registry_정리를_시도한다() {
        when(queueRepository.findActiveSessionIds()).thenReturn(Set.of(SESSION_ID));
        when(queueRepository.findExpiredAdmittedEntries(SESSION_ID, NOW, 50)).thenReturn(List.of());

        queueAdmissionService.expireAdmittedUsers();

        verify(queueRepository).removeActiveSessionIfEmpty(SESSION_ID);
    }

    @Test
    void 한_사용자_만료_처리_실패가_다른_사용자_처리를_막지_않는다() {
        QueueEntry failedEntry = waitingEntry(100L, 1L).admit(NOW.minus(Duration.ofMinutes(10)));
        QueueEntry succeedingEntry = waitingEntry(200L, 2L).admit(NOW.minus(Duration.ofMinutes(10)));
        when(queueRepository.findActiveSessionIds()).thenReturn(Set.of(SESSION_ID));
        when(queueRepository.findExpiredAdmittedEntries(SESSION_ID, NOW, 50))
                .thenReturn(List.of(failedEntry, succeedingEntry));
        when(queueRepository.expireIfAdmitted(failedEntry.expire(NOW), NOW))
                .thenThrow(new IllegalStateException("invalid entry"));

        queueAdmissionService.expireAdmittedUsers();

        verify(queueRepository).expireIfAdmitted(succeedingEntry.expire(NOW), NOW);
    }

    private QueueEntry waitingEntry(long userId, long sequence) {
        return QueueEntry.waiting(SESSION_ID, userId, sequence, NOW.minusSeconds(1), NOW.plus(Duration.ofHours(2)));
    }

    private boolean validTokenFor(AdmissionToken token, long userId) {
        assertThat(token.status()).isEqualTo(AdmissionTokenStatus.ACTIVE);
        assertThat(token.sessionId()).isEqualTo(SESSION_ID);
        assertThat(token.userId()).isEqualTo(userId);
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        return true;
    }
}
