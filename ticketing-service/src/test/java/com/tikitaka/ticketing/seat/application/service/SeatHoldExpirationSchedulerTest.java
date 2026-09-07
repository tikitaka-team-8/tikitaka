package com.tikitaka.ticketing.seat.application.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeatHoldExpirationSchedulerTest {

    @Mock
    private SeatService seatService;

    @Test
    void 만료_대상_id를_조회해서_각각_만료처리한다() {

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        given(seatService.findOverdueHoldIds(100)).willReturn(List.of(first, second));

        SeatHoldExpirationScheduler scheduler = new SeatHoldExpirationScheduler(seatService);
        scheduler.expireOverdueHolds();

        then(seatService).should().expireHold(first);
        then(seatService).should().expireHold(second);
    }

    @Test
    void 특정_건_만료처리가_실패해도_나머지_건은_계속_처리된다() {

        UUID failing = UUID.randomUUID();
        UUID succeeding = UUID.randomUUID();

        given(seatService.findOverdueHoldIds(100)).willReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("lock timeout")).when(seatService).expireHold(failing);

        SeatHoldExpirationScheduler scheduler = new SeatHoldExpirationScheduler(seatService);
        scheduler.expireOverdueHolds();

        then(seatService).should().expireHold(failing);
        then(seatService).should().expireHold(succeeding);
    }
}
