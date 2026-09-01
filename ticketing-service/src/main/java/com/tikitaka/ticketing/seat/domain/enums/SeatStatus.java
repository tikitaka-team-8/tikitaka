package com.tikitaka.ticketing.seat.domain.enums;

public enum SeatStatus {

    AVAILABLE,
    HELD,
    SOLD,
    EXCLUDED;

    public boolean canTransitionTo(SeatStatus next) {
        return switch (this) {
            case AVAILABLE -> next == HELD || next == EXCLUDED;
            case HELD -> next == AVAILABLE || next == SOLD;
            case SOLD -> false;
            case EXCLUDED -> next == AVAILABLE; //운영/관리자에 의한 판매 재개
        };
    }
}