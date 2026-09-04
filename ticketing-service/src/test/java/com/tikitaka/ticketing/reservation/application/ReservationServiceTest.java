package com.tikitaka.ticketing.reservation.application;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.exception.CommonErrorCode;
import com.tikitaka.ticketing.reservation.application.command.GetReservationCommand;
import com.tikitaka.ticketing.reservation.application.command.PaymentValidationCommand;
import com.tikitaka.ticketing.reservation.application.command.SearchReservationsCommand;
import com.tikitaka.ticketing.reservation.application.result.PaymentValidationResult;
import com.tikitaka.ticketing.reservation.application.result.ReservationResult;
import com.tikitaka.ticketing.reservation.application.result.ReservationSearchResult;
import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.entity.ReservationSeat;
import com.tikitaka.ticketing.reservation.domain.enums.ReservationStatus;
import com.tikitaka.ticketing.reservation.domain.model.ReservationSeatInfo;
import com.tikitaka.ticketing.reservation.domain.model.SeatHoldValidationInfo;
import com.tikitaka.ticketing.reservation.domain.port.ReservationRepositoryPort;
import com.tikitaka.ticketing.reservation.domain.port.SeatHoldQueryPort;
import com.tikitaka.ticketing.reservation.exception.ReservationErrorCode;
import com.tikitaka.ticketing.seat.domain.enums.HoldStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final UUID RESERVATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_SESSION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SCHEDULE_SEAT_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID SEAT_HOLD_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private ReservationRepositoryPort reservationRepositoryPort;

    @Mock
    private SeatHoldQueryPort seatHoldQueryPort;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    void 사용자는_자신의_예매_상세를_조회한다() {
        // given
        Reservation reservation = createReservation();
        ReservationSeatInfo seatDetail = createSeatDetail();
        GetReservationCommand command = new GetReservationCommand(OWNER_ID, "USER", RESERVATION_ID);

        given(reservationRepositoryPort.findById(RESERVATION_ID))
                .willReturn(Optional.of(reservation));
        given(reservationRepositoryPort.findSeatDetailsByReservationId(RESERVATION_ID))
                .willReturn(List.of(seatDetail));

        // when
        ReservationResult result = reservationService.getReservation(command);

        // then
        assertThat(result.getReservationId()).isEqualTo(RESERVATION_ID);
        assertThat(result.getUserId()).isEqualTo(OWNER_ID);
        assertThat(result.getReservationNumber()).isEqualTo("R-20260901-0001");
        assertThat(result.getSeats()).singleElement().satisfies(seat -> {
            assertThat(seat.getScheduleSeatId()).isEqualTo(SCHEDULE_SEAT_ID);
            assertThat(seat.getSection()).isEqualTo("A");
            assertThat(seat.getRowLabel()).isEqualTo("1");
            assertThat(seat.getSeatNumber()).isEqualTo("10");
            assertThat(seat.getSeatGrade()).isEqualTo("VIP");
            assertThat(seat.getPrice()).isEqualTo(50_000L);
        });
        verify(reservationRepositoryPort).findSeatDetailsByReservationId(RESERVATION_ID);
    }

    @Test
    void 관리자는_다른_사용자의_예매_상세도_조회한다() {
        // given
        Reservation reservation = createReservation();
        GetReservationCommand command = new GetReservationCommand(OTHER_USER_ID, "ADMIN", RESERVATION_ID);

        given(reservationRepositoryPort.findById(RESERVATION_ID))
                .willReturn(Optional.of(reservation));
        given(reservationRepositoryPort.findSeatDetailsByReservationId(RESERVATION_ID))
                .willReturn(List.of(createSeatDetail()));

        // when
        ReservationResult result = reservationService.getReservation(command);

        // then
        assertThat(result.getReservationId()).isEqualTo(RESERVATION_ID);
        assertThat(result.getUserId()).isEqualTo(OWNER_ID);
        verify(reservationRepositoryPort).findSeatDetailsByReservationId(RESERVATION_ID);
    }

    @Test
    void 사용자는_다른_사용자의_예매를_조회할_수_없다() {
        // given
        Reservation reservation = createReservation();
        GetReservationCommand command = new GetReservationCommand(OTHER_USER_ID, "USER", RESERVATION_ID);

        given(reservationRepositoryPort.findById(RESERVATION_ID))
                .willReturn(Optional.of(reservation));

        // when
        BusinessException exception = catchThrowableOfType(
                () -> reservationService.getReservation(command),
                BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_NOT_FOUND);
        verify(reservationRepositoryPort, never()).findSeatDetailsByReservationId(RESERVATION_ID);
    }

    @Test
    void 존재하지_않는_예매는_조회할_수_없다() {
        // given
        GetReservationCommand command = new GetReservationCommand(OWNER_ID, "USER", RESERVATION_ID);

        given(reservationRepositoryPort.findById(RESERVATION_ID))
                .willReturn(Optional.empty());

        // when
        BusinessException exception = catchThrowableOfType(
                () -> reservationService.getReservation(command),
                BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_NOT_FOUND);
        verify(reservationRepositoryPort, never()).findSeatDetailsByReservationId(RESERVATION_ID);
    }

    @Test
    void 사용자는_본인의_예매_목록을_검색한다() {
        // given
        Reservation reservation = createReservation();
        Pageable requestedPageable = PageRequest.of(2, 30, Sort.by(Sort.Direction.ASC, "sessionStartAt"));
        SearchReservationsCommand command = new SearchReservationsCommand(
                OWNER_ID, "USER", "  테스트 공연  ", null, requestedPageable);
        Page<Reservation> reservationPage = new PageImpl<>(List.of(reservation), requestedPageable, 61);

        given(reservationRepositoryPort.searchReservations(eq(OWNER_ID), eq("테스트 공연"), isNull(), any(Pageable.class)))
                .willReturn(reservationPage);

        // when
        Page<ReservationSearchResult> resultPage = reservationService.searchReservations(command);

        // then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(reservationRepositoryPort)
                .searchReservations(eq(OWNER_ID), eq("테스트 공연"), isNull(), pageableCaptor.capture());
        Pageable normalizedPageable = pageableCaptor.getValue();

        assertThat(normalizedPageable.getPageNumber()).isEqualTo(2);
        assertThat(normalizedPageable.getPageSize()).isEqualTo(30);
        assertThat(normalizedPageable.getSort().getOrderFor("sessionStartAt")).isNotNull().satisfies(order ->
                assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC));
        assertThat(resultPage.getContent()).singleElement().satisfies(result -> {
            assertThat(result.getReservationId()).isEqualTo(RESERVATION_ID);
            assertThat(result.getUserId()).isEqualTo(OWNER_ID);
            assertThat(result.getReservationNumber()).isEqualTo("R-20260901-0001");
        });
    }

    @Test
    void 관리자는_소유자_조건_없이_상태로_예매_목록을_검색한다() {
        // given
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        SearchReservationsCommand command = new SearchReservationsCommand(
                OTHER_USER_ID, "ADMIN", null, "CONFIRMED", pageable);
        Page<Reservation> reservationPage = new PageImpl<>(List.of(createReservation()), pageable, 1);

        given(reservationRepositoryPort.searchReservations(isNull(), isNull(), eq(ReservationStatus.CONFIRMED),
                any(Pageable.class))).willReturn(reservationPage);

        // when
        Page<ReservationSearchResult> resultPage = reservationService.searchReservations(command);

        // then
        assertThat(resultPage.getContent()).hasSize(1);
        verify(reservationRepositoryPort).searchReservations(isNull(), isNull(), eq(ReservationStatus.CONFIRMED),
                any(Pageable.class));
    }

    @Test
    void 잘못된_페이지와_크기를_기본값으로_보정하고_빈_목록을_반환한다() {
        // given
        Pageable requestedPageable = mock(Pageable.class);
        given(requestedPageable.getPageNumber()).willReturn(-1);
        given(requestedPageable.getPageSize()).willReturn(20);
        given(requestedPageable.getSort()).willReturn(Sort.unsorted());
        SearchReservationsCommand command = new SearchReservationsCommand(
                OWNER_ID, "USER", " ", " ", requestedPageable);
        Page<Reservation> emptyPage = Page.empty(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        given(reservationRepositoryPort.searchReservations(eq(OWNER_ID), isNull(), isNull(), any(Pageable.class)))
                .willReturn(emptyPage);

        // when
        Page<ReservationSearchResult> resultPage = reservationService.searchReservations(command);

        // then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(reservationRepositoryPort).searchReservations(eq(OWNER_ID), isNull(), isNull(), pageableCaptor.capture());
        Pageable normalizedPageable = pageableCaptor.getValue();

        assertThat(normalizedPageable.getPageNumber()).isZero();
        assertThat(normalizedPageable.getPageSize()).isEqualTo(10);
        assertThat(normalizedPageable.getSort().getOrderFor("createdAt")).isNotNull().satisfies(order ->
                assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC));
        assertThat(resultPage).isEmpty();
    }

    @Test
    void 존재하지_않는_예매_상태로는_목록을_조회할_수_없다() {
        // given
        SearchReservationsCommand command = new SearchReservationsCommand(
                OWNER_ID, "USER", null, "UNKNOWN", PageRequest.of(0, 10));

        // when
        BusinessException exception = catchThrowableOfType(
                () -> reservationService.searchReservations(command),
                BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
        verify(reservationRepositoryPort, never()).searchReservations(any(), any(), any(), any());
    }

    @Test
    void 허용하지_않는_필드로는_예매_목록을_정렬할_수_없다() {
        // given
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "totalAmount"));
        SearchReservationsCommand command = new SearchReservationsCommand(OWNER_ID, "USER", null, null, pageable);

        // when
        BusinessException exception = catchThrowableOfType(
                () -> reservationService.searchReservations(command),
                BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
        verify(reservationRepositoryPort, never()).searchReservations(any(), any(), any(), any());
    }

    @Test
    void 복수_정렬_조건으로는_예매_목록을_조회할_수_없다() {
        // given
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("sessionStartAt"));
        SearchReservationsCommand command = new SearchReservationsCommand(
                OWNER_ID, "USER", null, null, PageRequest.of(0, 10, sort));

        // when
        BusinessException exception = catchThrowableOfType(
                () -> reservationService.searchReservations(command),
                BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
        verify(reservationRepositoryPort, never()).searchReservations(any(), any(), any(), any());
    }

    @Test
    void 결제_가능한_예매를_검증한다() {
        // given
        Reservation reservation = createPaymentProcessingReservation();
        PaymentValidationCommand command = new PaymentValidationCommand(RESERVATION_ID, OWNER_ID);
        SeatHoldValidationInfo seatHold = createSeatHoldValidationInfo(
                OWNER_ID, HoldStatus.HOLDING, Instant.now().plus(Duration.ofMinutes(10)));

        given(reservationRepositoryPort.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));
        given(seatHoldQueryPort.findAllByIds(List.of(SEAT_HOLD_ID))).willReturn(List.of(seatHold));

        // when
        PaymentValidationResult result = reservationService.validatePayment(command);

        // then
        assertThat(result.getReservationId()).isEqualTo(RESERVATION_ID);
        assertThat(result.getUserId()).isEqualTo(OWNER_ID);
        assertThat(result.getTotalAmount()).isEqualTo(50_000L);
        verify(seatHoldQueryPort).findAllByIds(List.of(SEAT_HOLD_ID));
    }

    @Test
    void 존재하지_않는_예매는_결제_검증을_할_수_없다() {
        // given
        PaymentValidationCommand command = new PaymentValidationCommand(RESERVATION_ID, OWNER_ID);
        given(reservationRepositoryPort.findById(RESERVATION_ID)).willReturn(Optional.empty());

        // when
        BusinessException exception = catchThrowableOfType(
                () -> reservationService.validatePayment(command),
                BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_NOT_FOUND);
        verify(seatHoldQueryPort, never()).findAllByIds(any());
    }

    @Test
    void 예매자가_아니면_결제_검증을_할_수_없다() {
        // given
        PaymentValidationCommand command = new PaymentValidationCommand(RESERVATION_ID, OTHER_USER_ID);
        given(reservationRepositoryPort.findById(RESERVATION_ID)).willReturn(Optional.of(createReservation()));

        // when
        BusinessException exception = catchThrowableOfType(
                () -> reservationService.validatePayment(command),
                BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_NOT_FOUND);
        verify(seatHoldQueryPort, never()).findAllByIds(any());
    }

    @Test
    void 결제_처리중_상태가_아니면_결제_검증을_할_수_없다() {
        // given
        PaymentValidationCommand command = new PaymentValidationCommand(RESERVATION_ID, OWNER_ID);
        given(reservationRepositoryPort.findById(RESERVATION_ID)).willReturn(Optional.of(createReservation()));

        // when
        BusinessException exception = catchThrowableOfType(
                () -> reservationService.validatePayment(command),
                BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_PAYMENT_NOT_ALLOWED);
        verify(seatHoldQueryPort, never()).findAllByIds(any());
    }

    @Test
    void 연결된_좌석_선점을_찾을_수_없으면_결제_검증을_할_수_없다() {
        // given
        Reservation reservation = createPaymentProcessingReservation();
        PaymentValidationCommand command = new PaymentValidationCommand(RESERVATION_ID, OWNER_ID);

        given(reservationRepositoryPort.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));
        given(seatHoldQueryPort.findAllByIds(List.of(SEAT_HOLD_ID))).willReturn(List.of());

        // when
        BusinessException exception = catchThrowableOfType(
                () -> reservationService.validatePayment(command),
                BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.SEAT_HOLD_NOT_FOUND);
    }

    @Test
    void 다른_사용자의_좌석_선점은_결제_검증을_할_수_없다() {
        // given
        Reservation reservation = createPaymentProcessingReservation();
        PaymentValidationCommand command = new PaymentValidationCommand(RESERVATION_ID, OWNER_ID);
        SeatHoldValidationInfo seatHold = createSeatHoldValidationInfo(
                OTHER_USER_ID, HoldStatus.HOLDING, Instant.now().plus(Duration.ofMinutes(10)));

        given(reservationRepositoryPort.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));
        given(seatHoldQueryPort.findAllByIds(List.of(SEAT_HOLD_ID))).willReturn(List.of(seatHold));

        // when
        BusinessException exception = catchThrowableOfType(
                () -> reservationService.validatePayment(command),
                BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.SEAT_HOLD_OWNERSHIP_REQUIRED);
    }

    @Test
    void 선점중이_아닌_좌석은_결제_검증을_할_수_없다() {
        // given
        Reservation reservation = createPaymentProcessingReservation();
        PaymentValidationCommand command = new PaymentValidationCommand(RESERVATION_ID, OWNER_ID);
        SeatHoldValidationInfo seatHold = createSeatHoldValidationInfo(
                OWNER_ID, HoldStatus.CONFIRMED, Instant.now().plus(Duration.ofMinutes(10)));

        given(reservationRepositoryPort.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));
        given(seatHoldQueryPort.findAllByIds(List.of(SEAT_HOLD_ID))).willReturn(List.of(seatHold));

        // when
        BusinessException exception = catchThrowableOfType(
                () -> reservationService.validatePayment(command),
                BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.INVALID_SEAT_HOLD_STATUS);
    }

    @Test
    void 만료된_좌석_선점은_결제_검증을_할_수_없다() {
        // given
        Reservation reservation = createPaymentProcessingReservation();
        PaymentValidationCommand command = new PaymentValidationCommand(RESERVATION_ID, OWNER_ID);
        SeatHoldValidationInfo seatHold = createSeatHoldValidationInfo(
                OWNER_ID, HoldStatus.HOLDING,Instant.now().minusSeconds(1));

        given(reservationRepositoryPort.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));
        given(seatHoldQueryPort.findAllByIds(List.of(SEAT_HOLD_ID))).willReturn(List.of(seatHold));

        // when
        BusinessException exception = catchThrowableOfType(
                () -> reservationService.validatePayment(command),
                BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.SEAT_HOLD_EXPIRED);
    }

    private Reservation createReservation() {
        Reservation reservation = Reservation.create(
                OWNER_ID,
                EVENT_ID,
                EVENT_SESSION_ID,
                "R-20260901-0001",
                "테스트 공연",
                Instant.parse("2026-09-01T10:00:00Z"),
                1,
                50_000L,
                "reservation-request-1",
                List.of()
        );
        ReflectionTestUtils.setField(reservation, "reservationId", RESERVATION_ID);
        return reservation;
    }

    private Reservation createPaymentProcessingReservation() {
        Reservation reservation = createReservation();
        reservation.updateStatus(ReservationStatus.PAYMENT_PROCESSING, OWNER_ID);

        ReservationSeat reservationSeat = mock(ReservationSeat.class);
        given(reservationSeat.getSeatHoldId()).willReturn(SEAT_HOLD_ID);
        ReflectionTestUtils.setField(reservation, "reservationSeats", List.of(reservationSeat));

        return reservation;
    }

    private SeatHoldValidationInfo createSeatHoldValidationInfo(Long userId, HoldStatus holdStatus, Instant expiresAt) {
        return new SeatHoldValidationInfo(SEAT_HOLD_ID, userId, holdStatus, expiresAt);
    }

    private ReservationSeatInfo createSeatDetail() {
        return new ReservationSeatInfo(
                SCHEDULE_SEAT_ID,
                "A",
                "1",
                "10",
                "VIP",
                50_000L
        );
    }
}
