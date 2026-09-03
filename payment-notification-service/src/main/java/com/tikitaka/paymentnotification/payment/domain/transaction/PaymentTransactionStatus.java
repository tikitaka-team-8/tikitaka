package com.tikitaka.paymentnotification.payment.domain.transaction;

public enum PaymentTransactionStatus {
    SUCCESS,
    FAILED,
    UNKNOWN // 실제 상태를 모르는 경우 : 실제로 결제O OR 우리 서버가 응답 못받음
}
