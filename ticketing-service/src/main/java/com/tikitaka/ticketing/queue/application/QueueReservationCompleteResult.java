package com.tikitaka.ticketing.queue.application;

public enum QueueReservationCompleteResult {
    COMPLETED,
    ALREADY_COMPLETED,
    STALE_FLOW,
    UNBOUND_CURRENT_ENTRY,
    CURRENT_RESERVATION_MISMATCH,
    NO_OP,
    FAILED
}
