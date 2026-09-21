package com.tikitaka.paymentnotification.payment.infrastructure.pg.toss;

import com.tikitaka.paymentnotification.payment.application.gateway.PaymentGatewayResult;
import com.tikitaka.paymentnotification.payment.infrastructure.pg.toss.dto.TossErrorResponse;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TossErrorMapperTest {


    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TossErrorMapper tossErrorMapper;

    @Test
    void 명확한_결제_거절은_FAILED로_처리한다() throws Exception {
        FeignException exception = mock(FeignException.class);

        when(exception.contentUTF8())
                .thenReturn("""
                        {
                          "code": "REJECT_CARD_PAYMENT",
                          "message": "카드 결제가 거절되었습니다."
                        }
                        """);

        when(objectMapper.readValue(
                anyString(),
                eq(TossErrorResponse.class)
        )).thenReturn(
                new TossErrorResponse(
                        "REJECT_CARD_PAYMENT",
                        "카드 결제가 거절되었습니다."
                )
        );

        PaymentGatewayResult result =
                tossErrorMapper.map(exception);

        assertThat(result.status())
                .isEqualTo(PaymentGatewayResult.Status.FAILED);

        assertThat(result.failureCode())
                .isEqualTo("REJECT_CARD_PAYMENT");

        assertThat(result.failureReason())
                .isEqualTo("카드 결제가 거절되었습니다.");
    }

    @Test
    void 결과가_불명확한_Toss_오류는_예외를_재전파한다() throws Exception {
        FeignException exception = mock(FeignException.class);

        when(exception.contentUTF8()).thenReturn("{}");

        when(objectMapper.readValue(
                anyString(),
                eq(TossErrorResponse.class)
        )).thenReturn(
                new TossErrorResponse(
                        "PROVIDER_ERROR",
                        "일시적인 오류입니다."
                )
        );

        assertThatThrownBy(() -> tossErrorMapper.map(exception))
                .isSameAs(exception);
    }

    @Test
    void 알_수_없는_Toss_오류도_예외를_재전파한다() throws Exception {
        FeignException exception = mock(FeignException.class);

        when(exception.contentUTF8()).thenReturn("{}");

        when(objectMapper.readValue(
                anyString(),
                eq(TossErrorResponse.class)
        )).thenReturn(
                new TossErrorResponse(
                        "SOME_NEW_TOSS_ERROR",
                        "알 수 없는 오류"
                )
        );

        assertThatThrownBy(() -> tossErrorMapper.map(exception))
                .isSameAs(exception);
    }
}


