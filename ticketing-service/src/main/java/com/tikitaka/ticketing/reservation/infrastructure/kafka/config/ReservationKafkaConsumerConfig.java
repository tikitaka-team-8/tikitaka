package com.tikitaka.ticketing.reservation.infrastructure.kafka.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.reservation.infrastructure.kafka.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class ReservationKafkaConsumerConfig {

    private static final long RETRY_INTERVAL_MS = 2_000L;
    private static final long RETRY_ATTEMPTS = 2L;

    // Spring Boot 공통 Consumer 설정을 적용하고 Reservation 전용 오류 처리 정책을 연결
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> reservationKafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate
    ) {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(reservationKafkaErrorHandler(kafkaTemplate));

        return factory;
    }

    private DefaultErrorHandler reservationKafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {

        // 최종 처리에 실패한 원본 이벤트를 동일한 파티션 번호의 DLT로 발행
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, this::resolveDltDestination);
        // DLT 발행이 실패하면 원본 이벤트까지 처리 완료된 것으로 넘어가지 않도록 예외 전달
        recoverer.setFailIfSendResultIsError(true);

        // 최초 처리 후 2초 간격으로 2회 재시도하여 총 3회 처리
        return createErrorHandler(recoverer, reservationRetryBackOff());
    }

    DefaultErrorHandler createErrorHandler(ConsumerRecordRecoverer recoverer, BackOff backOff) {

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // 재처리해도 복구되지 않는 비즈니스·이벤트 형식 오류는 즉시 DLT로 이동
        errorHandler.addNotRetryableExceptions(BusinessException.class, JsonProcessingException.class);

        return errorHandler;
    }

    FixedBackOff reservationRetryBackOff() {
        return new FixedBackOff(RETRY_INTERVAL_MS, RETRY_ATTEMPTS);
    }

    TopicPartition resolveDltDestination(ConsumerRecord<?, ?> record, Exception exception) {
        // DLT 메시지 추적에 필요한 원본 Topic, Partition, Offset을 로그로 기록
        log.error(
                "Payment 이벤트 최종 처리 실패로 DLT 전송: topic={}, partition={}, offset={}, dltTopic={}",
                record.topic(), record.partition(), record.offset(), KafkaTopics.PAYMENT_EVENTS_DLT, exception
        );

        return new TopicPartition(KafkaTopics.PAYMENT_EVENTS_DLT, record.partition());
    }
}
