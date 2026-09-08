package com.tikitaka.paymentnotification.payment.application;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGateway;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayRequest;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayResult;
import com.tikitaka.paymentnotification.payment.application.gateway.ReservationPaymentValidator;
import com.tikitaka.paymentnotification.payment.application.result.ReservationPaymentValidationResult;
import com.tikitaka.paymentnotification.payment.domain.payment.*;
import com.tikitaka.paymentnotification.payment.exception.PaymentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.BDDMockito.given;
@SpringBootTest
@ActiveProfiles("local")
@Disabled("실제 PostgreSQL이 필요한 동시성 통합 테스트 - CI 테스트 DB 환경 구성 후 활성화")
class PaymentApprovalConcurrencyTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private ReservationPaymentValidator reservationPaymentValidator;

    @MockitoBean
    private PaymentGateway paymentGateway;

    private UUID paymentId;
    private UUID reservationId;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {

        reservationId = UUID.randomUUID();

        Payment payment = Payment.create(
                reservationId,
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
    void 동시에_결제_승인을_요청해도_PG는_한번만_호출된다() throws Exception {

        // given
        int threadCount = 10;

        given(
                reservationPaymentValidator.validate(
                        any(UUID.class),
                        anyLong()
                )
        ).willReturn(
                new ReservationPaymentValidationResult(
                        reservationId,
                        1L,
                        150000L
                )
        );

        /*
         * PG 호출을 조금 지연시킨다.
         *
         * 첫 번째 요청이 PROCESSING을 선점한 상태에서
         * 다른 요청들이 approvePayment()에 진입할 수 있도록 하기 위함.
         */
        given(
                paymentGateway.approve(
                        any(PaymentGatewayRequest.class)
                )
        ).willAnswer(invocation -> {
            Thread.sleep(300);

            return PaymentGatewayResult.success(
                    "pg-payment-key"
            );
        });


        ExecutorService executorService =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch readyLatch =
                new CountDownLatch(threadCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<?>> futures =
                new ArrayList<>();


        try {

            // when
            for (int i = 0; i < threadCount; i++) {

                Future<?> future =
                        executorService.submit(() -> {

                            // 모든 스레드가 준비될 때까지 대기
                            readyLatch.countDown();

                            // 동시에 시작
                            startLatch.await();

                            try {

                                paymentService.approvePayment(
                                        paymentId,
                                        USER_ID,
                                        PaymentMethod.CARD
                                );

                            } catch (PaymentException ignored) {

                                /*
                                 * CAS 선점에 실패한 요청은
                                 * PROCESSING 상태를 확인하고
                                 * PAYMENT_NOT_ALLOWED가 발생할 수 있다.
                                 */
                            }

                            return null;
                        });

                futures.add(future);
            }


            // 모든 스레드 준비 완료 대기
            readyLatch.await();

            // 동시에 승인 요청 시작
            startLatch.countDown();


            // 모든 작업 종료 대기
            for (Future<?> future : futures) {
                future.get();
            }


            // then

            // Ticketing 결제 검증도 선점한 요청만 호출
            verify(
                    reservationPaymentValidator,
                    times(1)
            ).validate(
                    any(UUID.class),
                    anyLong()
            );


            // 핵심 검증:
            // 여러 승인 요청이 들어와도 PG는 한 번만 호출되어야 한다.
            verify(
                    paymentGateway,
                    times(1)
            ).approve(
                    any(PaymentGatewayRequest.class)
            );


            // 정상적인 승자 요청은 최종적으로 APPROVED가 되어야 한다.
            Payment payment =
                    paymentRepository.findById(paymentId)
                            .orElseThrow();

            assertThat(payment.getStatus())
                    .isEqualTo(PaymentStatus.APPROVED);

        } finally {

            executorService.shutdownNow();
        }
    }
}