package com.tikitaka.ticketing.queue.application;

import com.tikitaka.ticketing.queue.domain.AdmissionToken;
import com.tikitaka.ticketing.queue.domain.QueueEntry;

public record QueueStatusResult(
        QueueEntry entry,
        Long position,
        AdmissionToken admissionToken
) {
}
