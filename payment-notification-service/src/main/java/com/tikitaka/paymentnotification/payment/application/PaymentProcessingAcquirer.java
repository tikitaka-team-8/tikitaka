package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentProcessingAcquirer {

    private final PaymentRepository paymentRepository;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean acquire(UUID paymentId){
        return paymentRepository.tryStartProcessing(paymentId);

    }


}
