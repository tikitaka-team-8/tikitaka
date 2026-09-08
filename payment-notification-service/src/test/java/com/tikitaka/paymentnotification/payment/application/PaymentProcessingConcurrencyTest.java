package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.domain.payment.Payment;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentProvider;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentRepository;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Disabled("실제 PostgreSQL이 필요한 동시성 통합 테스트 - CI 테스트 DB 환경 구성 후 활성화")
class PaymentProcessingConcurrencyTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentProcessingAcquirer paymentProcessingAcquirer;

    private UUID paymentId;

    @BeforeEach
    void setUp() {
        Payment payment = Payment.create(
                UUID.randomUUID(),
                1L,
                "PAY-" + UUID.randomUUID(),
                "idempotency-" + UUID.randomUUID(),
                150000L,
                PaymentProvider.MOCK
        );

        Payment savedPayment = paymentRepository.save(payment);

        paymentId = savedPayment.getPaymentId();
    }

    @Test
    void 동시에_여러_요청이_PROCESSING_선점을_시도하면_하나만_성공한다()
            throws InterruptedException {

        // given
        int threadCount = 10;

        ExecutorService executorService =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch readyLatch =
                new CountDownLatch(threadCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        CountDownLatch doneLatch =
                new CountDownLatch(threadCount);

        AtomicInteger successCount =
                new AtomicInteger();

        AtomicInteger failureCount =
                new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    // 모든 스레드가 출발 준비 완료
                    readyLatch.countDown();

                    // 동시에 출발할 때까지 대기
                    startLatch.await();

                    boolean acquired =
                            paymentProcessingAcquirer.acquire(paymentId);

                    if (acquired) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();

                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 모든 스레드가 준비될 때까지 대기
        readyLatch.await();

        // 동시에 출발
        startLatch.countDown();

        // 모든 스레드 작업 완료까지 대기
        doneLatch.await();

        executorService.shutdown();

        // then
        assertThat(successCount.get())
                .isEqualTo(1);

        assertThat(failureCount.get())
                .isEqualTo(threadCount - 1);

        Payment payment =
                paymentRepository.findById(paymentId)
                        .orElseThrow();

        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.PROCESSING);
    }
}