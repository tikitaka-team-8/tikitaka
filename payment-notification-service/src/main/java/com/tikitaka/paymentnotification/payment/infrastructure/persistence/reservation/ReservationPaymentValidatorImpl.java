package com.tikitaka.paymentnotification.payment.infrastructure.persistence.reservation;

import com.tikitaka.paymentnotification.global.exception.BusinessException;
import com.tikitaka.paymentnotification.global.exception.CommonErrorCode;
import com.tikitaka.paymentnotification.payment.application.gateway.ReservationPaymentValidator;
import com.tikitaka.paymentnotification.payment.application.result.ReservationPaymentValidationResult;
import com.tikitaka.paymentnotification.payment.exception.PaymentErrorCode;
import com.tikitaka.paymentnotification.payment.exception.PaymentException;
import com.tikitaka.paymentnotification.payment.infrastructure.reservation.ReservationFeignClient;
import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReservationPaymentValidatorImpl implements ReservationPaymentValidator {

    private final ReservationFeignClient reservationFeignClient;

    @Value("${internal.ticketing-service-key}")
    private String ticketingServiceKey;

    @Override
    public ReservationPaymentValidationResult validate(UUID reservationId, Long userId) {
        try {
            ReservationPaymentValidationResponse response =
                    reservationFeignClient.validatePayment(
                            ticketingServiceKey,
                            reservationId,
                            new ReservationPaymentValidationRequest(userId)
                    );

            return new ReservationPaymentValidationResult(
                    response.reservationId(),
                    response.userId(),
                    response.totalAmount()
            );

        } catch (RetryableException e) {

            if (e.getCause() instanceof SocketTimeoutException) {
                throw new BusinessException(
                        CommonErrorCode.DOWNSTREAM_SERVICE_TIMEOUT
                );
            }

            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE
            );

        } catch (FeignException.FeignServerException e) {

            throw new BusinessException(
                    CommonErrorCode.DOWNSTREAM_SERVICE_FAILURE
            );

        } catch (FeignException.FeignClientException e) {

            throw new PaymentException(
                    PaymentErrorCode.PAYMENT_NOT_ALLOWED
            );
        }
    }

    private BusinessException handleRetryableException(
            RetryableException exception
    ) {
        if (exception.getCause() instanceof SocketTimeoutException) {
            return new BusinessException(
                    CommonErrorCode.DOWNSTREAM_SERVICE_TIMEOUT
            );
        }

        return new BusinessException(
                CommonErrorCode.SERVICE_UNAVAILABLE
        );
    }
}
