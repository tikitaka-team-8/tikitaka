package com.tikitaka.paymentnotification.payment.exception;

public class PaymentEventPublishException extends RuntimeException{

    public PaymentEventPublishException(String message , Throwable cause){
        super(message,cause);
    }
}
