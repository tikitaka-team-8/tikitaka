package com.tikitaka.ticketing.seat.application.service;

import java.util.List;
import java.util.UUID;


import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeatHoldExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SeatHoldExpirationScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final SeatService seatService;

    @Scheduled(fixedDelayString = "${seat.hold.expiration-interval:PT30S}")
    public void expireOverdueHolds() {
        List<UUID> overdueHoldIds = seatService.findOverdueHoldIds(BATCH_SIZE);

        for (UUID seatHoldId : overdueHoldIds) {
            try {
                seatService.expireHold(seatHoldId);
            } catch (Exception exception) {
                log.warn("좌석 선점 만료 처리 실패. seatHoldId={}", seatHoldId, exception);
            }
        }
    }
}


