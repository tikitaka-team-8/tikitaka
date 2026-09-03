package com.tikitaka.paymentnotification.payment.application.gateway;

public record PaymentGatewayResult(
        Status status,
        String pgPaymentKey,
        String failureCode,
        String failureReason

){
    public enum Status{
        SUCCESS,
        FAILED,
        UNKNOWN
    }

    public static PaymentGatewayResult success (String pgPaymentKey){
        return new PaymentGatewayResult(Status.SUCCESS, pgPaymentKey, null, null);
    }

    public static PaymentGatewayResult failed (String failureCode, String failureReason){
        return new PaymentGatewayResult(Status.FAILED, null, failureCode, failureReason);
    }

    public static PaymentGatewayResult unknown(){
        return new PaymentGatewayResult(Status.UNKNOWN,null, null,null);
    }

}
