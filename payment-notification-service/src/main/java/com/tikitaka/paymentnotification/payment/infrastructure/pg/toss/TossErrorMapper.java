package com.tikitaka.paymentnotification.payment.infrastructure.pg.toss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayResult;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentMethod;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentStatus;
import com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.dto.TossErrorResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class TossErrorMapper {

    private final ObjectMapper objectMapper;

    public PaymentGatewayResult map (FeignException exception){
        TossErrorResponse error = parseError(exception);

        return switch (error.code()) {

            // 결제 거절이 명확한 경우
            case "REJECT_CARD_PAYMENT",
                 "REJECT_ACCOUNT_PAYMENT",
                 "REJECT_CARD_COMPANY",
                 "EXCEED_MAX_AUTH_COUNT",
                 "NOT_SUPPORTED_METHOD" ->
                    PaymentGatewayResult.failed(
                            error.code(),
                            error.message()
                    );

            // 결과를 확신하기 어려운 경우
            case "PROVIDER_ERROR",
                 "FAILED_INTERNAL_SYSTEM_PROCESSING",
                 "FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING",
                 "FAILED_CARD_COMPANY_RESPONSE" ->
                    throw exception;

            // 요청/인증/설정 오류 등
            default ->
                    throw exception;
        };
    }

    private TossErrorResponse parseError(FeignException exception) {
        try {
            return objectMapper.readValue(
                    exception.contentUTF8(),
                    TossErrorResponse.class
            );
        } catch (JsonProcessingException e) {
            throw exception;
        }
    }


}
