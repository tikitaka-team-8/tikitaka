package com.tikitaka.paymentnotification.notification.infrastructure.kafka.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tikitaka.paymentnotification.global.exception.BusinessException;
import com.tikitaka.paymentnotification.global.exception.CommonErrorCode;
import com.tikitaka.paymentnotification.notification.infrastructure.kafka.KafkaTopics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.FixedBackOff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaConsumerConfigTest {

    @Mock
    private ConsumerRecordRecoverer recoverer;

    @Mock
    private Consumer<?, ?> consumer;

    @Mock
    private MessageListenerContainer container;

    private NotificationKafkaConsumerConfig config;
    private ConsumerRecord<String, String> record;

    @BeforeEach
    void setUp() {
        config = new NotificationKafkaConsumerConfig();
        record = new ConsumerRecord<>(KafkaTopics.RESERVATION_EVENTS, 0, 10L, "reservation-id", "payload");
    }

    @Test
    void 재시도는_2초_간격으로_2회_수행한다() {
        // given
        BackOffExecution execution = config.notificationRetryBackOff().start();

        // when
        long firstRetryInterval = execution.nextBackOff();
        long secondRetryInterval = execution.nextBackOff();
        long nextInterval = execution.nextBackOff();

        // then
        assertThat(firstRetryInterval).isEqualTo(2_000L);
        assertThat(secondRetryInterval).isEqualTo(2_000L);
        assertThat(nextInterval).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void BusinessException은_재시도하지_않고_즉시_복구_처리한다() {
        // given
        DefaultErrorHandler errorHandler = config.createErrorHandler(recoverer, new FixedBackOff(0L, 2L));
        BusinessException exception = new BusinessException(CommonErrorCode.INVALID_INPUT);

        // when
        boolean recovered = errorHandler.handleOne(exception, record, consumer, container);

        // then
        assertThat(recovered).isTrue();
        verify(recoverer).accept(record, exception);
    }

    @Test
    void JsonProcessingException은_재시도하지_않고_즉시_복구_처리한다() {
        // given
        DefaultErrorHandler errorHandler = config.createErrorHandler(recoverer, new FixedBackOff(0L, 2L));
        JsonProcessingException exception = new JsonProcessingException("올바르지 않은 JSON") {
        };

        // when
        boolean recovered = errorHandler.handleOne(exception, record, consumer, container);

        // then
        assertThat(recovered).isTrue();
        verify(recoverer).accept(record, exception);
    }

    @Test
    void 재시도_가능한_예외는_최초_포함_3회_실패하면_복구_처리한다() {
        // given
        DefaultErrorHandler errorHandler = config.createErrorHandler(recoverer, new FixedBackOff(0L, 2L));
        RuntimeException exception = new RuntimeException("일시적 오류");

        // when
        boolean firstAttemptRecovered = errorHandler.handleOne(exception, record, consumer, container);
        boolean secondAttemptRecovered = errorHandler.handleOne(exception, record, consumer, container);
        boolean thirdAttemptRecovered = errorHandler.handleOne(exception, record, consumer, container);

        // then
        assertThat(firstAttemptRecovered).isFalse();
        assertThat(secondAttemptRecovered).isFalse();
        assertThat(thirdAttemptRecovered).isTrue();
        verify(recoverer).accept(record, exception);
    }

    @Test
    void Reservation_이벤트의_DLT는_원본과_동일한_파티션을_사용한다() {
        // given
        Exception exception = new RuntimeException("최종 처리 실패");

        // when
        TopicPartition destination = config.resolveDltDestination(record, exception);

        // then
        assertThat(destination.topic()).isEqualTo(KafkaTopics.RESERVATION_EVENTS_DLT);
        assertThat(destination.partition()).isEqualTo(record.partition());
    }
}
