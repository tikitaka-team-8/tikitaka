package com.tikitaka.ticketing.reservation.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.queue.application.QueueReservationFlow;
import com.tikitaka.ticketing.queue.exception.QueueErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueueReservationFlowAdapterTest {
    private static final UUID EVENT_SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final long USER_ID = 1L;

    @Mock
    private QueueReservationFlow queueReservationFlow;

    private QueueReservationFlowAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new QueueReservationFlowAdapter(queueReservationFlow);
    }

    @Test
    void Queue_일시_장애도_원래_예외와_오류_코드를_유지한다() {
        BusinessException exception = new BusinessException(QueueErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        willThrow(exception)
                .given(queueReservationFlow)
                .complete(EVENT_SESSION_ID, USER_ID, RESERVATION_ID);

        assertThatThrownBy(() -> adapter.completeReservationFlow(EVENT_SESSION_ID, USER_ID, RESERVATION_ID))
                .isSameAs(exception);
    }

    @Test
    void Queue_비즈니스_오류는_기존_예외를_유지한다() {
        willThrow(new BusinessException(QueueErrorCode.QUEUE_ENTRY_STATE_CONFLICT))
                .given(queueReservationFlow)
                .complete(EVENT_SESSION_ID, USER_ID, RESERVATION_ID);

        assertThatThrownBy(() -> adapter.completeReservationFlow(EVENT_SESSION_ID, USER_ID, RESERVATION_ID))
                .isInstanceOf(BusinessException.class);
    }
}
