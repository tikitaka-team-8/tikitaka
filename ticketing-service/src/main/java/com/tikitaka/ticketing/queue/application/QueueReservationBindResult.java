package com.tikitaka.ticketing.queue.application;

public enum QueueReservationBindResult {
    BOUND,
    ENTRY_NOT_FOUND,
    NOT_ENTERED,
    RESERVATION_CONFLICT,
    STALE_FLOW,
    FAILED
}
