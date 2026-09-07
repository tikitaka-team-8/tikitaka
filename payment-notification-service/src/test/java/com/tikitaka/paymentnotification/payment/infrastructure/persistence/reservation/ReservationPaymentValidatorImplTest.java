package com.tikitaka.paymentnotification.payment.infrastructure.persistence.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tikitaka.paymentnotification.global.security.InternalServiceKeyProperties;
import com.tikitaka.paymentnotification.payment.application.result.ReservationPaymentValidationResult;
import com.tikitaka.paymentnotification.payment.infrastructure.reservation.ReservationFeignClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationPaymentValidatorImplTest {

    private static final String SERVICE_KEY = "test-internal-service-key";
    private static final UUID RESERVATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Long USER_ID = 1L;
    private static final Long TOTAL_AMOUNT = 150_000L;

    @Mock
    private ReservationFeignClient reservationFeignClient;

    @Test
    void 공통_내부_서비스_키로_예매_검증_API를_호출한다() {
        InternalServiceKeyProperties properties = new InternalServiceKeyProperties(SERVICE_KEY);
        ReservationPaymentValidatorImpl validator =
                new ReservationPaymentValidatorImpl(reservationFeignClient, properties);
        ReservationPaymentValidationRequest request = new ReservationPaymentValidationRequest(USER_ID);
        when(reservationFeignClient.validatePayment(eq(SERVICE_KEY), eq(RESERVATION_ID), eq(request)))
                .thenReturn(new ReservationPaymentValidationResponse(RESERVATION_ID, USER_ID, TOTAL_AMOUNT));

        ReservationPaymentValidationResult result = validator.validate(RESERVATION_ID, USER_ID);

        assertThat(result.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.totalAmount()).isEqualTo(TOTAL_AMOUNT);
        verify(reservationFeignClient).validatePayment(SERVICE_KEY, RESERVATION_ID, request);
    }
}
