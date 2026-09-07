package com.tikitaka.paymentnotification.payment.infrastructure.messaging;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentEventPublisher;
import com.tikitaka.paymentnotification.payment.exception.PaymentEventPublishException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void publish(String topic, UUID key, String payload) {

        try{
            kafkaTemplate.send(topic,key.toString(),payload).join();
        }catch (RuntimeException e){
            throw new PaymentEventPublishException("Payment 이벤트 Kafka 발행에 실패했습니다.",e);
        }


    }
}
