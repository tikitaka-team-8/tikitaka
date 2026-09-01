package com.tikitaka.paymentnotification.payment.domain.transaction;

import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentProvider;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "p_payment_transaction",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_payment_transaction_attempt",
                        columnNames = {
                                "payment_id",
                                "transaction_type",
                                "attempt_no"
                        }
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction {
    @Id
    @UuidGenerator
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private PaymentTransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private PaymentProvider provider;

    @Column(name = "pg_transaction_id", length = 200)
    private String pgTransactionId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentTransactionStatus status;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;




    public static PaymentTransaction createApproveSuccess(
            Payment payment,
            PaymentProvider provider,
            String pgTransactionId,
            Long amount,
            int attemptNo,
            OffsetDateTime requestedAt
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.payment = payment;
        transaction.transactionType = PaymentTransactionType.APPROVE;
        transaction.provider = provider;
        transaction.pgTransactionId = pgTransactionId;
        transaction.amount = amount;
        transaction.status = PaymentTransactionStatus.SUCCESS;
        transaction.attemptNo = attemptNo;
        transaction.failureCode = null;
        transaction.failureReason = null;
        transaction.requestedAt = requestedAt;
        transaction.completedAt = now;
        transaction.createdAt = now;
        transaction.updatedAt = now;

        return transaction;
    }

    public static PaymentTransaction createApproveFailed(
            Payment payment,
            PaymentProvider provider,
            Long amount,
            int attemptNo,
            String failureCode,
            String failureReason,
            OffsetDateTime requestedAt
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.payment = payment;
        transaction.transactionType = PaymentTransactionType.APPROVE;
        transaction.provider = provider;
        transaction.amount = amount;
        transaction.status = PaymentTransactionStatus.FAILED;
        transaction.attemptNo = attemptNo;
        transaction.failureCode = failureCode;
        transaction.failureReason = failureReason;
        transaction.requestedAt = requestedAt;
        transaction.completedAt = now;
        transaction.createdAt = now;
        transaction.updatedAt = now;

        return transaction;
    }

    public static PaymentTransaction createApproveUnknown(
            Payment payment,
            PaymentProvider provider,
            Long amount,
            int attemptNo,
            OffsetDateTime requestedAt
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.payment = payment;
        transaction.transactionType = PaymentTransactionType.APPROVE;
        transaction.provider = provider;
        transaction.amount = amount;
        transaction.status = PaymentTransactionStatus.UNKNOWN;
        transaction.attemptNo = attemptNo;
        transaction.requestedAt = requestedAt;
        transaction.completedAt = now;
        transaction.createdAt = now;
        transaction.updatedAt = now;

        return transaction;
    }




}
