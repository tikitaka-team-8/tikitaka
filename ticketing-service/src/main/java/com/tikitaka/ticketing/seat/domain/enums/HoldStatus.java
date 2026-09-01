package com.tikitaka.ticketing.seat.domain.enums;

public enum HoldStatus {

    HOLDING,
    CONFIRMED,
    EXPIRED,
    RELEASED;

    public boolean canTransitionTo(HoldStatus next) {
        return switch (this) {
            case HOLDING -> next == CONFIRMED
                    || next == EXPIRED
                    || next == RELEASED;

            case CONFIRMED, EXPIRED, RELEASED -> false;
        };
    }
}