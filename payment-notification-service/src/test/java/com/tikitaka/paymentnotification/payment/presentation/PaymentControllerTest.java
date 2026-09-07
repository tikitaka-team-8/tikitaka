package com.tikitaka.paymentnotification.payment.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tikitaka.paymentnotification.payment.application.PaymentService;
import com.tikitaka.paymentnotification.payment.application.result.PaymentApproveResult;
import com.tikitaka.paymentnotification.payment.application.result.PaymentDetailResult;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentMethod;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentProvider;
import com.tikitaka.paymentnotification.payment.domain.payment.PaymentStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final UUID PAYMENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Long USER_ID = 1L;

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentService)).build();
    }

    @Test
    void 결제조회는_Gateway_사용자_ID를_Service에_전달한다() throws Exception {
        when(paymentService.getPaymentById(PAYMENT_ID, USER_ID)).thenReturn(new PaymentDetailResult(
                PAYMENT_ID,
                RESERVATION_ID,
                "PAY-test-order",
                150_000L,
                PaymentStatus.READY,
                "KRW",
                null,
                PaymentProvider.MOCK,
                null,
                null
        ));

        mockMvc.perform(get("/api/v1/payments/{paymentId}", PAYMENT_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("READY"));

        verify(paymentService).getPaymentById(PAYMENT_ID, USER_ID);
    }

    @Test
    void 결제승인은_Gateway_사용자_ID를_Service에_전달한다() throws Exception {
        when(paymentService.approvePayment(PAYMENT_ID, USER_ID, PaymentMethod.CARD))
                .thenReturn(new PaymentApproveResult(PAYMENT_ID, PaymentStatus.APPROVED, null, null));

        mockMvc.perform(post("/api/v1/payments/{paymentId}/approve", PAYMENT_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        verify(paymentService).approvePayment(PAYMENT_ID, USER_ID, PaymentMethod.CARD);
    }

    @Test
    void Gateway_사용자_ID가_없으면_결제조회를_호출할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{paymentId}", PAYMENT_ID))
                .andExpect(status().isBadRequest());
    }
}
