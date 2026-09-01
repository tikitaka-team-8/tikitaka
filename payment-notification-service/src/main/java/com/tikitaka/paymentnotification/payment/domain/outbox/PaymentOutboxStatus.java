package com.tikitaka.paymentnotification.payment.domain.outbox;

public enum PaymentOutboxStatus {
    PENDING, // Kafka 발행 성공
    PUBLISHED, // 발행 실패 / 재시도
    FAILED // 최대 재시도 초과
}
