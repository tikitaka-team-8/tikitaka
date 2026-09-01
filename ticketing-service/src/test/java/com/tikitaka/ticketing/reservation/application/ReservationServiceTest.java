package com.tikitaka.ticketing.reservation.application;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.reservation.application.command.GetReservationCommand;
import com.tikitaka.ticketing.reservation.application.result.ReservationResult;
import com.tikitaka.ticketing.reservation.domain.entity.Reservation;
import com.tikitaka.ticketing.reservation.domain.model.ReservationSeatDetail;
import com.tikitaka.ticketing.reservation.domain.port.ReservationRepositoryPort;
import com.tikitaka.ticketing.reservation.exception.ReservationErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final UUID RESERVATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_SESSION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SCHEDULE_SEAT_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private ReservationRepositoryPort reservationRepositoryPort;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    void 사용자는_자신의_예매_상세를_조회한다() {
        // given
        Reservation reservation = createReservation();
        ReservationSeatDetail seatDetail = createSeatDetail();
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

    private ReservationSeatDetail createSeatDetail() {
        return new ReservationSeatDetail(
                SCHEDULE_SEAT_ID,
                "A",
                "1",
                "10",
                "VIP",
                50_000L
        );
    }
}
