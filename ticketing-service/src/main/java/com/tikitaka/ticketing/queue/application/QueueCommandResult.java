package com.tikitaka.ticketing.queue.application;

import com.tikitaka.ticketing.queue.domain.QueueEntry;

public record QueueCommandResult(
        QueueEntry entry,
        boolean existingEntry
) {
}
