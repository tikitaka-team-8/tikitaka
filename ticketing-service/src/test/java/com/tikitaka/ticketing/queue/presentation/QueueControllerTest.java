package com.tikitaka.ticketing.queue.presentation;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tikitaka.ticketing.queue.application.QueueService;
import com.tikitaka.ticketing.queue.application.QueueStatusResult;
import com.tikitaka.ticketing.queue.domain.AdmissionToken;
import com.tikitaka.ticketing.queue.domain.AdmissionTokenStatus;
import com.tikitaka.ticketing.queue.domain.QueueEntry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QueueController.class)
@AutoConfigureMockMvc(addFilters = false)
class QueueControllerTest {
    private static final UUID SESSION_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final long USER_ID = 7L;
    private static final Instant JOINED_AT = Instant.parse("2026-09-03T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueueService queueService;

    @Test
    void 대기열에_진입하고_현재_순번을_반환한다() throws Exception {
        QueueEntry entry = QueueEntry.waiting(SESSION_ID, USER_ID, 3L, JOINED_AT, JOINED_AT.plusSeconds(7200));
        QueueStatusResult result = new QueueStatusResult(entry, 3L, null);
        given(queueService.enterQueue(SESSION_ID, USER_ID)).willReturn(entry);
        given(queueService.getQueueStatus(SESSION_ID, USER_ID)).willReturn(result);

        mockMvc.perform(post("/api/v1/event-sessions/{sessionId}/queue", SESSION_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.sessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.position").value(3))
                .andExpect(jsonPath("$.data.admissionToken").doesNotExist());

        verify(queueService).enterQueue(SESSION_ID, USER_ID);
        verify(queueService).getQueueStatus(SESSION_ID, USER_ID);
    }

    @Test
    void admitted_상태는_입장_토큰과_만료_시각을_반환한다() throws Exception {
        QueueEntry entry = QueueEntry.waiting(SESSION_ID, USER_ID, 3L, JOINED_AT, JOINED_AT.plusSeconds(7200))
                .admit(JOINED_AT.plusSeconds(30));
        AdmissionToken token = new AdmissionToken(
                "admission-token",
                SESSION_ID,
                USER_ID,
                JOINED_AT.plusSeconds(630),
                AdmissionTokenStatus.ACTIVE
        );
        given(queueService.getQueueStatus(SESSION_ID, USER_ID))
                .willReturn(new QueueStatusResult(entry, null, token));

        mockMvc.perform(get("/api/v1/event-sessions/{sessionId}/queue/me", SESSION_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("ADMITTED"))
                .andExpect(jsonPath("$.data.position").doesNotExist())
                .andExpect(jsonPath("$.data.admissionToken").value(token.token()))
                .andExpect(jsonPath("$.data.expiresAt").value(token.expiresAt().toString()));

        verify(queueService).getQueueStatus(SESSION_ID, USER_ID);
    }
}
