package com.tikitaka.paymentnotification.payment.domain.outbox;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class PaymentOutboxTest {

    private Payment payment;
    private PaymentOutbox outbox;

    @BeforeEach
    void setUp() {
        payment = Payment.create(
                UUID.randomUUID(),
                1L,
                "PAY-test-order",
                "test-idempotency-key",
                150000L,
                "KRW",
                PaymentProvider.MOCK
        );

        outbox = PaymentOutbox.create(
                payment,
                "PAYMENT_SUCCEEDED",
                "{\"eventType\":\"PAYMENT_SUCCEEDED\"}"
        );
    }

    @Test
    void Outbox_발행에_성공하면_PUBLISHED_상태가_된다() {
        // when
        outbox.markPublished();

        // then
        assertThat(outbox.getStatus())
                .isEqualTo(PaymentOutboxStatus.PUBLISHED);

        assertThat(outbox.getPublishedAt())
                .isNotNull();
    }

    @Test
    void Outbox_발행에_실패하면_재시도_횟수가_증가한다() {
        // when
        outbox.recordPublishFailure(3);

        // then
        assertThat(outbox.getStatus())
                .isEqualTo(PaymentOutboxStatus.PENDING);

        assertThat(outbox.getRetryCount())
                .isEqualTo(1);
    }

    @Test
    void Outbox_발행이_최대_재시도_횟수만큼_실패하면_FAILED_상태가_된다() {
        // when
        outbox.recordPublishFailure(3);
        outbox.recordPublishFailure(3);
        outbox.recordPublishFailure(3);

        // then
        assertThat(outbox.getStatus())
                .isEqualTo(PaymentOutboxStatus.FAILED);

        assertThat(outbox.getRetryCount())
                .isEqualTo(3);
    }

    @Test
    void PENDING이_아닌_Outbox는_다시_처리할_수_없다() {
        // given
        outbox.markPublished();

        // when & then
        assertThatThrownBy(() -> outbox.markPublished())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PENDING 상태의 Outbox만 처리할 수 있습니다.");
    }

    @Test
    void FAILED_Outbox는_발행_실패_처리를_더_진행할_수_없다() {
        // given
        outbox.recordPublishFailure(3);
        outbox.recordPublishFailure(3);
        outbox.recordPublishFailure(3);

        // when & then
        assertThatThrownBy(() -> outbox.recordPublishFailure(3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PENDING 상태의 Outbox만 처리할 수 있습니다.");

        assertThat(outbox.getRetryCount())
                .isEqualTo(3);
    }


}