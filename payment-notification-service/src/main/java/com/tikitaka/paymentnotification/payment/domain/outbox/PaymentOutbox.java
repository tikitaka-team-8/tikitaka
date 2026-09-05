package com.tikitaka.paymentnotification.payment.domain.outbox;

import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_payment_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOutbox {

    @Id
    @UuidGenerator
    @Column(name = "outbox_id", nullable = false, updatable = false)
    private UUID outboxId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentOutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;


    public static PaymentOutbox create(
            Payment payment,
            String eventType,
            String payload
    ) {
        PaymentOutbox outbox = new PaymentOutbox();

        outbox.payment = payment;
        outbox.eventType = eventType;
        outbox.payload = payload;
        outbox.status = PaymentOutboxStatus.PENDING;
        outbox.retryCount = 0;

        OffsetDateTime now = OffsetDateTime.now();
        outbox.createdAt = now;
        outbox.updatedAt = now;

        return outbox;
    }


    // 상태 변경
    public void markPublished() {
        validatePendingStatus();

        OffsetDateTime now = OffsetDateTime.now();

        this.status = PaymentOutboxStatus.PUBLISHED;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void recordPublishFailure(int maxRetryCount) {
        validatePendingStatus();

        this.retryCount++;

        if (this.retryCount >= maxRetryCount) {
            this.status = PaymentOutboxStatus.FAILED;
        }

        this.updatedAt = OffsetDateTime.now();
    }

    private void validatePendingStatus() {
        if (this.status != PaymentOutboxStatus.PENDING) {
            throw new IllegalStateException(
                    "PENDING 상태의 Outbox만 처리할 수 있습니다."
            );
        }
    }

}
