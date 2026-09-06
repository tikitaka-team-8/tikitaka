package com.tikitaka.ticketing.reservation.presentation;

import com.tikitaka.ticketing.reservation.application.ReservationService;
import com.tikitaka.ticketing.reservation.application.command.CreateReservationCommand;
import com.tikitaka.ticketing.reservation.application.command.SearchReservationsCommand;
import com.tikitaka.ticketing.reservation.application.result.CreateReservationResult;
import com.tikitaka.ticketing.reservation.application.result.ReservationSearchResult;
import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.presentation.controller.ReservationController;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@WebMvcTest(ReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationControllerTest {

    private static final UUID RESERVATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PAYMENT_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Long USER_ID = 1L;
    private static final Long ADMIN_ID = 2L;
    private static final String IDEMPOTENCY_KEY = "reservation-request-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @Test
    void 신규_예매를_생성하면_201을_반환한다() throws Exception {
        // given
        given(reservationService.createReservation(any(CreateReservationCommand.class)))
                .willReturn(createReservationResult(true));

        // when
        mockMvc.perform(post("/api/v1/reservations")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"seatHoldIds":["50000000-0000-0000-0000-000000000001"]}
                                """))

                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.reservationId").value(RESERVATION_ID.toString()))
                .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.data.reservationStatus").value("PAYMENT_PROCESSING"))
                .andExpect(jsonPath("$.data.seatCount").value(1))
                .andExpect(jsonPath("$.data.totalAmount").value(50_000));

        ArgumentCaptor<CreateReservationCommand> commandCaptor =
                ArgumentCaptor.forClass(CreateReservationCommand.class);
        verify(reservationService).createReservation(commandCaptor.capture());
        CreateReservationCommand command = commandCaptor.getValue();
        assertThat(command.getLoginUserId()).isEqualTo(USER_ID);
        assertThat(command.getUserRole()).isEqualTo("USER");
        assertThat(command.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(command.getSeatHoldIds()).containsExactly(
                UUID.fromString("50000000-0000-0000-0000-000000000001"));
    }

    @Test
    void 동일한_멱등_요청이면_기존_예매와_200을_반환한다() throws Exception {
        // given
        given(reservationService.createReservation(any(CreateReservationCommand.class)))
                .willReturn(createReservationResult(false));

        // when
        mockMvc.perform(post("/api/v1/reservations")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"seatHoldIds":["50000000-0000-0000-0000-000000000001"]}
                                """))

                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.reservationId").value(RESERVATION_ID.toString()));
    }

    @Test
    void 사용자의_검색조건과_페이징으로_예매_목록을_조회한다() throws Exception {
        // given
        PageRequest pageable = PageRequest.of(0, 30, Sort.by(Sort.Direction.ASC, "sessionStartAt"));
        Page<ReservationSearchResult> resultPage = new PageImpl<>(
                List.of(createSearchResult()), pageable, 35);
        given(reservationService.searchReservations(any(SearchReservationsCommand.class))).willReturn(resultPage);

        // when
        mockMvc.perform(get("/api/v1/reservations")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .param("page", "0")
                        .param("size", "30")
                        .param("sort", "sessionStartAt,asc")
                        .param("eventTitle", "테스트 공연"))

                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("예매 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data[0].reservationId").value(RESERVATION_ID.toString()))
                .andExpect(jsonPath("$.data[0].reservationNumber").value("R-20260901-0001"))
                .andExpect(jsonPath("$.data[0].eventTitle").value("테스트 공연"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(30))
                .andExpect(jsonPath("$.meta.totalElements").value(35))
                .andExpect(jsonPath("$.meta.totalPages").value(2))
                .andExpect(jsonPath("$.meta.hasNext").value(true));

        ArgumentCaptor<SearchReservationsCommand> commandCaptor =
                ArgumentCaptor.forClass(SearchReservationsCommand.class);
        verify(reservationService).searchReservations(commandCaptor.capture());
        SearchReservationsCommand command = commandCaptor.getValue();

        assertThat(command.getLoginUserId()).isEqualTo(USER_ID);
        assertThat(command.getUserRole()).isEqualTo("USER");
        assertThat(command.getEventTitle()).isEqualTo("테스트 공연");
        assertThat(command.getReservationStatus()).isNull();
        assertThat(command.getPageable().getPageNumber()).isZero();
        assertThat(command.getPageable().getPageSize()).isEqualTo(30);
        assertThat(command.getPageable().getSort().getOrderFor("sessionStartAt")).isNotNull().satisfies(order ->
                assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC));
    }

    @Test
    void 관리자는_기본_페이징으로_예매_목록을_조회한다() throws Exception {
        // given
        Page<ReservationSearchResult> emptyPage = Page.empty(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));
        given(reservationService.searchReservations(any(SearchReservationsCommand.class))).willReturn(emptyPage);

        // when
        mockMvc.perform(get("/api/v1/reservations")
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Role", "ADMIN"))

                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(10))
                .andExpect(jsonPath("$.meta.totalElements").value(0))
                .andExpect(jsonPath("$.meta.totalPages").value(0))
                .andExpect(jsonPath("$.meta.hasNext").value(false));

        ArgumentCaptor<SearchReservationsCommand> commandCaptor =
                ArgumentCaptor.forClass(SearchReservationsCommand.class);
        verify(reservationService).searchReservations(commandCaptor.capture());
        SearchReservationsCommand command = commandCaptor.getValue();

        assertThat(command.getLoginUserId()).isEqualTo(ADMIN_ID);
        assertThat(command.getUserRole()).isEqualTo("ADMIN");
        assertThat(command.getPageable().getSort().getOrderFor("createdAt")).isNotNull().satisfies(order ->
                assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC));
    }

    @Test
    void 존재하지_않는_예매_상태는_입력값_오류를_반환한다() throws Exception {
        // given
        String invalidStatus = "UNKNOWN";

        // when
        mockMvc.perform(get("/api/v1/reservations")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .param("reservationStatus", invalidStatus))

                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C-002"));

        verify(reservationService, never()).searchReservations(any(SearchReservationsCommand.class));
    }

    @Test
    void 검색조건을_두개_입력하면_입력값_오류를_반환한다() throws Exception {
        // given
        String eventTitle = "테스트 공연";
        String reservationStatus = "CONFIRMED";

        // when
        mockMvc.perform(get("/api/v1/reservations")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .param("eventTitle", eventTitle)
                        .param("reservationStatus", reservationStatus))

                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C-002"));

        verify(reservationService, never()).searchReservations(any(SearchReservationsCommand.class));
    }

    private ReservationSearchResult createSearchResult() {
        Reservation reservation = Reservation.create(
                USER_ID,
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                "R-20260901-0001",
                "테스트 공연",
                Instant.parse("2026-09-01T10:00:00Z"),
                1,
                50_000L,
                "reservation-request-1",
                List.of()
        );
        ReflectionTestUtils.setField(reservation, "reservationId", RESERVATION_ID);
        return new ReservationSearchResult(reservation);
    }

    private CreateReservationResult createReservationResult(boolean created) {
        Reservation reservation = Reservation.create(
                USER_ID,
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                "RSV-260901-8F3A91C2D7E4",
                "테스트 공연",
                Instant.parse("2026-09-01T10:00:00Z"),
                1,
                50_000L,
                IDEMPOTENCY_KEY,
                List.of()
        );
        ReflectionTestUtils.setField(reservation, "reservationId", RESERVATION_ID);
        ReflectionTestUtils.setField(reservation, "createdAt", Instant.parse("2026-09-01T09:50:00Z"));
        reservation.markAsPaymentProcessing(PAYMENT_ID, USER_ID);
        return new CreateReservationResult(reservation, created);
    }
}
