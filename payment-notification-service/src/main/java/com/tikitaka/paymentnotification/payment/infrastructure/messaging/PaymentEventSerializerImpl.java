package com.tikitaka.paymentnotification.payment.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentEventSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventSerializerImpl implements PaymentEventSerializer {

    private final ObjectMapper objectMapper;
    @Override
    public String serialize(Object event) {

        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Payment 이벤트 직렬화에 실패했습니다.",e);
        }

    }
}
