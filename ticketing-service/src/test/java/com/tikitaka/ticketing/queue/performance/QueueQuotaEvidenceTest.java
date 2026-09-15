package com.tikitaka.ticketing.queue.performance;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QueueQuotaEvidenceTest {
    private static List<Map<String, Object>> successes(int count, long before, long after) {
        var rows = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < count; i++) rows.add(Map.of("success", true, "redisBeforeMs", before, "redisAfterMs", after));
        return rows;
    }
    @Test void failsWhenDefinitelyOverLimit() {
        assertEquals("FAIL", QueueMultiInstanceVerification.quotaEvidence(successes(51, 1100, 1900)).get("verdict"));
    }
    @Test void acceptsLimitAndIndependentTimeWindows() {
        var rows = successes(50, 1100, 1900);
        rows.addAll(successes(50, 2100, 2900));
        assertEquals("PASS", QueueMultiInstanceVerification.quotaEvidence(rows).get("verdict"));
    }
    @Test void doesNotAssignBoundaryCrossingToResponseWindow() {
        var rows = successes(50, 1100, 1900);
        rows.addAll(successes(1, 1999, 2001));
        assertEquals("INCONCLUSIVE", QueueMultiInstanceVerification.quotaEvidence(rows).get("verdict"));
    }
    @Test void ignoresUnsuccessfulAttempts() {
        var rows = successes(50, 1100, 1900);
        rows.add(Map.of("success", false));
        assertEquals("PASS", QueueMultiInstanceVerification.quotaEvidence(rows).get("verdict"));
    }
    @Test void rejectsMissingEvidenceAndBackwardsClock() {
        assertEquals("INCONCLUSIVE", QueueMultiInstanceVerification.quotaEvidence(List.of()).get("verdict"));
        assertEquals("INCONCLUSIVE", QueueMultiInstanceVerification.quotaEvidence(successes(1, 2000, 1000)).get("verdict"));
    }
}
