package com.tikitaka.paymentnotification.payment.domain.transaction;

public enum PaymentTransactionType {
    APPROVE, // 결제 승인 시도
    CANCEL // 승인된 결제 취소 시도
}
