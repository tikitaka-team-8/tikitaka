package com.tikitaka.ticketing.queue.application;

import java.util.UUID;

/**
 * Seat domain uses this port to authorize a user's first access to the seat area.
 */
public interface QueueAdmissionValidator {

    /**
     * Authorizes a user's first access to the seat area and marks the Queue entry as entered.
     */
    void validateAndEnter(UUID sessionId, long userId, String admissionToken);

    /**
     * Authorizes a Seat request after the user has already entered the seat area.
     */
    void validateEntered(UUID sessionId, long userId);
}
