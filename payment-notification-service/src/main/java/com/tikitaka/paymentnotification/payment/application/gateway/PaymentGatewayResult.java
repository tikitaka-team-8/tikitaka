package com.tikitaka.paymentnotification.payment.application.gateway;

import com.tikitaka.paymentnotification.payment.domain.payment.PaymentMethod;

public record PaymentGatewayResult(
        Status status,
        String pgPaymentKey,
        PaymentMethod paymentMethod,
        String failureCode,
        String failureReason

){
    public enum Status{
        SUCCESS,
        FAILED,
        UNKNOWN
    }

    public static PaymentGatewayResult success (String pgPaymentKey, PaymentMethod paymentMethod){
        return new PaymentGatewayResult(Status.SUCCESS, pgPaymentKey, paymentMethod, null, null);
    }

    public static PaymentGatewayResult failed (String failureCode, String failureReason){
        return new PaymentGatewayResult(Status.FAILED, null, null, failureCode, failureReason);
    }

    public static PaymentGatewayResult unknown(){
        return new PaymentGatewayResult(Status.UNKNOWN,null, null, null,null);
    }

    public static PaymentGatewayResult unknown(String code, String reason) {
        return new PaymentGatewayResult(
                Status.UNKNOWN, null, null, code, reason
        );
    }

}
