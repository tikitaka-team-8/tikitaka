package com.tikitaka.ticketing.reservation.infrastructure.kafka.producer;

import com.tikitaka.ticketing.reservation.infrastructure.kafka.KafkaTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
public class ReservationEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public ReservationEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(String reservationId, String payload) throws ExecutionException, InterruptedException {
        kafkaTemplate.send(KafkaTopics.RESERVATION_EVENTS, reservationId, payload).get();
    }
}
