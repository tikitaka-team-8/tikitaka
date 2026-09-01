package com.tikitaka.ticketing.queue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdmissionTokenTest {

    private static final UUID SESSION_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-01T01:10:00Z");

    @Test
    void activeTokenCanBeUsed() {
        AdmissionToken token = token(AdmissionTokenStatus.ACTIVE);

        AdmissionToken usedToken = token.use();

        assertThat(usedToken.status()).isEqualTo(AdmissionTokenStatus.USED);
    }

    @Test
    void activeTokenCanExpire() {
        AdmissionToken token = token(AdmissionTokenStatus.ACTIVE);

        AdmissionToken expiredToken = token.expire();

        assertThat(expiredToken.status()).isEqualTo(AdmissionTokenStatus.EXPIRED);
    }

    @Test
    void usedTokenCannotTransitionAgain() {
        AdmissionToken token = token(AdmissionTokenStatus.USED);

        assertThatIllegalStateException().isThrownBy(token::use);
        assertThatIllegalStateException().isThrownBy(token::expire);
    }

    @Test
    void expiredTokenCannotTransitionAgain() {
        AdmissionToken token = token(AdmissionTokenStatus.EXPIRED);

        assertThatIllegalStateException().isThrownBy(token::use);
        assertThatIllegalStateException().isThrownBy(token::expire);
    }

    private AdmissionToken token(AdmissionTokenStatus status) {
        return new AdmissionToken("token-1", SESSION_ID, 1L, EXPIRES_AT, status);
    }
}
