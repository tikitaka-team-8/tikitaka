package com.tikitaka.paymentnotification.payment.domain.payment;

public enum PaymentStatus {
    READY, // 결제 생성됨, PG 승인 요청 전
    PROCESSING, // PG 승인 처리중
    APPROVED, // 결제 승인 완료
    FAILED, //결제 실패 확정
    CANCELED, // 승인된 결제 취소
    UNKNOWN // PG응답 유실 등으로 결과 확정X
}
