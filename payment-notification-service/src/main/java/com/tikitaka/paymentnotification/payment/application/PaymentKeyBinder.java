package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentRepository;
import com.tikitaka.paymentnotification.payment.exception.PaymentErrorCode;
import com.tikitaka.paymentnotification.payment.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentKeyBinder {

    private final PaymentRepository paymentRepository;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void bind(UUID paymentId, String paymentKey){
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(()->
                new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        payment.bindPaymentKey(paymentKey);
    }


}
