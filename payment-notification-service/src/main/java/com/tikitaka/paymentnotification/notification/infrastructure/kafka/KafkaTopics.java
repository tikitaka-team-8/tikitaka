package com.tikitaka.paymentnotification.notification.infrastructure.kafka;

public final class KafkaTopics {

    public static final String RESERVATION_EVENTS = "reservation-events";
    public static final String RESERVATION_EVENTS_DLT = RESERVATION_EVENTS + ".DLT";

    private KafkaTopics() {
    }
}
