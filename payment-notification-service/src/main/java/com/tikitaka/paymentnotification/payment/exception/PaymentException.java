package com.tikitaka.paymentnotification.payment.exception;

import com.tikitaka.paymentnotification.global.exception.BusinessException;
import com.tikitaka.paymentnotification.global.exception.ErrorCode;

public class PaymentException extends BusinessException {
    public PaymentException(PaymentErrorCode errorCode) {
        super(errorCode);
    }
}
