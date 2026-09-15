package com.tikitaka.paymentnotification.payment.infrastructure.pg.toss;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.toss")
public record TossPaymentProperties (
        String baseUrl,
        String secretKey
){
}
