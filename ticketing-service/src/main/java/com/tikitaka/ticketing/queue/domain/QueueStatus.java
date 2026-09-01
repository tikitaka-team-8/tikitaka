package com.tikitaka.ticketing.queue.domain;

public enum QueueStatus {
    WAITING,
    ADMITTED,
    ENTERED,
    EXPIRED;

    public boolean isActive() {
        return this == WAITING || this == ADMITTED || this == ENTERED;
    }
}
