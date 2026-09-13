package com.tikitaka.paymentnotification.payment.infrastructure.pg.toss;

import com.tikitaka.paymentnotification.payment.application.gateway.*;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentMethod;
import com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.dto.TossConfirmRequest;
import com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.dto.TossPaymentResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "payment.provider",
        havingValue = "toss"
)
public class TossPaymentGateway implements PaymentGateway , PaymentQueryGateway {

    private final TossPaymentsFeignClient tossPaymentsFeignClient;
    private final TossPaymentProperties tossPaymentProperties;
    private final TossErrorMapper tossErrorMapper;

    @Override
    public PaymentGatewayResult approve(PaymentGatewayRequest request) {

        try {
            TossPaymentResponse response =
                    tossPaymentsFeignClient.confirm(
                            createAuthorization(),
                            new TossConfirmRequest(
                                    request.paymentKey(),
                                    request.orderId(),
                                    request.amount()
                            )
                    );

            return toResult(response);

        } catch (FeignException e) {
            return tossErrorMapper.map(e);
        }
    }

    // 헤더 값 생성
    private String createAuthorization(){
        String credentials = tossPaymentProperties.secretKey()+":";

        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private PaymentMethod mapPaymentMethod(String method){
        if(method == null){
            return PaymentMethod.OTHER;
        }

        return switch (method){
            case "카드" -> PaymentMethod.CARD;
            case "간편결제" -> PaymentMethod.EASY_PAY;
            default -> PaymentMethod.OTHER;
        };
    }

    private PaymentGatewayResult toResult(TossPaymentResponse response) {
        if(!"DONE".equals(response.status())){
            return PaymentGatewayResult.unknown(
                    "TOSS_UNEXPECTED_STATUS",
                    "Unexpected Toss payment status: " + response.status()
            );
        }

        PaymentMethod paymentMethod = mapPaymentMethod(response.method());

        if(paymentMethod == PaymentMethod.OTHER) {
            return PaymentGatewayResult.unknown(
                    "TOSS_UNSUPPORTED_PAYMENT_METHOD",
                    "Unsupported Toss payment method: " + response.method()
            );
        }

        return PaymentGatewayResult.success(response.paymentKey(), paymentMethod);
    }


    // UNKNOWN 상태를 복구하기 위한 조회
    @Override
    public PaymentQueryResult getPayment(String paymentKey) {

        String secretKey = tossPaymentProperties.secretKey();

        TossPaymentResponse response =
                tossPaymentsFeignClient.getPayment(
                        createAuthorization(),
                        paymentKey
                );

        return new PaymentQueryResult(
                response.paymentKey(),
                response.orderId(),
                response.totalAmount(),
                response.status(),
                mapPaymentMethod(response.method())
        );
    }
}
