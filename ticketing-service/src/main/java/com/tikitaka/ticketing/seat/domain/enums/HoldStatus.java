package com.tikitaka.ticketing.seat.domain.enums;

public enum HoldStatus {

    HOLDING,
    RESERVED,
    CONFIRMED,
    EXPIRED,
    RELEASED;

    public boolean canTransitionTo(HoldStatus next) {
        return switch (this) {
            case HOLDING -> next == RESERVED
                    || next == EXPIRED
                    || next == RELEASED;

            case RESERVED -> next == CONFIRMED
                    || next == RELEASED;

            case CONFIRMED, EXPIRED, RELEASED -> false;
        };
    }
}
