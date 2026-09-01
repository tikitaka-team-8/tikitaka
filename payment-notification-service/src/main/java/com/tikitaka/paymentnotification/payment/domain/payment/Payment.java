package com.tikitaka.paymentnotification.payment.domain.payment;

import com.tikitaka.paymentnotification.payment.exception.PaymentErrorCode;
import com.tikitaka.paymentnotification.payment.exception.PaymentException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="p_payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
    @Id
    @UuidGenerator
    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false, length = 100, unique = true)
    private String orderId;

    @Column(name = "idempotency_key", nullable = false, length = 100, unique = true)
    private String idempotencyKey;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", nullable = false, length = 30)
    private PaymentProvider paymentProvider;

    @Column(name = "pg_payment_key", length = 200)
    private String pgPaymentKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "canceled_at")
    private OffsetDateTime canceledAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;



    public static Payment create(
            UUID reservationId,
            Long userId,
            String orderId,
            String idempotencyKey,
            Long amount,
            String currency,
            PaymentProvider paymentProvider
    ) {
        Payment payment = new Payment();

        payment.reservationId = reservationId;
        payment.userId = userId;
        payment.orderId = orderId;
        payment.idempotencyKey = idempotencyKey;
        payment.amount = amount;
        payment.currency = currency;
        payment.paymentProvider = paymentProvider;
        payment.status = PaymentStatus.READY;

        OffsetDateTime now = OffsetDateTime.now();

        payment.requestedAt = now;
        payment.createdAt = now;
        payment.updatedAt = now;

        return payment;
    }

    private static void validateAmount(Long amount) {
        if (amount == null || amount < 0) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_REQUEST);
        }
    }

    public void startProcessing() {
        validateStatus(PaymentStatus.READY);

        this.status = PaymentStatus.PROCESSING;
        this.updatedAt = OffsetDateTime.now();
    }

    public void fail(
            String failureCode,
            String failureReason
    ) {
        validateStatus(PaymentStatus.PROCESSING);

        this.status = PaymentStatus.FAILED;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markUnknown() {
        validateStatus(PaymentStatus.PROCESSING);

        this.status = PaymentStatus.UNKNOWN;
        this.updatedAt = OffsetDateTime.now();
    }

    public void cancel() {
        validateCancelable();

        OffsetDateTime now = OffsetDateTime.now();

        this.status = PaymentStatus.CANCELED;
        this.canceledAt = now;
        this.updatedAt = now;
    }

    public boolean isSameRequest(
            UUID reservationId,
            Long userId,
            Long amount,
            String currency,
            PaymentProvider paymentProvider
    ) {
        return this.reservationId.equals(reservationId)
                && this.userId.equals(userId)
                && this.amount.equals(amount)
                && this.currency.equals(currency)
                && this.paymentProvider == paymentProvider;
    }


    public void approve(
            PaymentMethod paymentMethod,
            String pgPaymentKey
    ) {
        validateApprovable();

        OffsetDateTime now = OffsetDateTime.now();

        this.paymentMethod = paymentMethod;
        this.pgPaymentKey = pgPaymentKey;
        this.status = PaymentStatus.APPROVED;
        this.approvedAt = now;
        this.updatedAt = now;
    }

    private void validateCancelable() {
        if (this.status == PaymentStatus.CANCELED) {
            throw new PaymentException(
                    PaymentErrorCode.PAYMENT_ALREADY_CANCELLED
            );
        }

        if (this.status != PaymentStatus.APPROVED) {
            throw new PaymentException(
                    PaymentErrorCode.PAYMENT_CANCELLATION_NOT_ALLOWED
            );
        }
    }

    private void validateApprovable() {
        if (this.status == PaymentStatus.APPROVED) {
            throw new PaymentException(
                    PaymentErrorCode.PAYMENT_ALREADY_COMPLETED
            );
        }

        if (this.status != PaymentStatus.PROCESSING) {
            throw new PaymentException(
                    PaymentErrorCode.PAYMENT_NOT_ALLOWED
            );
        }
    }
    private void validateStatus(PaymentStatus expectedStatus) {
        if (this.status != expectedStatus) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_ALLOWED);
        }
    }
}
