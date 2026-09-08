package com.tikitaka.paymentnotification.payment.application;


import com.tikitaka.paymentnotification.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentProcessingCompensator {

    private final PaymentRepository paymentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean restoreReady(UUID paymentId) {
        return paymentRepository.tryRestoreReady(paymentId);
    }


}
