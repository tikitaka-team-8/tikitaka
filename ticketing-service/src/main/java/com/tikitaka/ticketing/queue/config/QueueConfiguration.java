package com.tikitaka.ticketing.queue.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueConfiguration {

    @Bean
    Clock queueClock() {
        return Clock.systemUTC();
    }
}
