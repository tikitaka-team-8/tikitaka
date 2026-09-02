package com.tikitaka.ticketing.seat;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.seat.application.service.SeatService;
import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import com.tikitaka.ticketing.seat.domain.repository.ScheduleSeatRepository;
import com.tikitaka.ticketing.seat.exception.SeatErrorCode;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatListResponse;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock
    private ScheduleSeatRepository scheduleSeatRepository;

    @InjectMocks
    private SeatService seatService;

    private UUID eventSessionId;
    private UUID scheduleSeatId;

    @BeforeEach
    void setUp() {
        eventSessionId = UUID.randomUUID();
        scheduleSeatId = UUID.randomUUID();
    }

    @Test
    void 회차_좌석_상세를_조회한다() {

        ScheduleSeat seat = mock(ScheduleSeat.class);

        when(scheduleSeatRepository.findSeatDetail(
                eventSessionId,
                scheduleSeatId
        )).thenReturn(Optional.of(seat));

        ScheduleSeatResponse response =
                seatService.getSeatDetail(
                        eventSessionId,
                        scheduleSeatId
                );

        assertThat(response).isNotNull();

        verify(scheduleSeatRepository)
                .findSeatDetail(
                        eventSessionId,
                        scheduleSeatId
                );
    }

    @Test
    void 존재하지_않는_회차_좌석을_조회하면_예외가_발생한다() {

        when(scheduleSeatRepository.findSeatDetail(
                eventSessionId,
                scheduleSeatId
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                seatService.getSeatDetail(
                        eventSessionId,
                        scheduleSeatId
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode()
                )
                .isEqualTo(SeatErrorCode.SESSION_OR_SEAT_NOT_FOUND);

        verify(scheduleSeatRepository)
                .findSeatDetail(
                        eventSessionId,
                        scheduleSeatId
                );
    }

    @Test
    void 회차_좌석_목록을_조회한다() {

        ScheduleSeat seat1 = mock(ScheduleSeat.class);
        ScheduleSeat seat2 = mock(ScheduleSeat.class);
        ScheduleSeat seat3 = mock(ScheduleSeat.class);

        List<ScheduleSeat> seats =
                List.of(seat1, seat2, seat3);

        when(scheduleSeatRepository.findSeats(
                eventSessionId,
                null,
                null
        )).thenReturn(seats);

        ScheduleSeatListResponse response =
                seatService.getSeatList(
                        eventSessionId,
                        null,
                        null
                );

        assertThat(response).isNotNull();
        verify(scheduleSeatRepository)
                .findSeats(
                        eventSessionId,
                        null,
                        null
                );
    }

    @Test
    void section과_grade로_좌석_목록을_조회한다() {

        ScheduleSeat seat1 = mock(ScheduleSeat.class);
        ScheduleSeat seat2 = mock(ScheduleSeat.class);
        when(scheduleSeatRepository.findSeats(
                eventSessionId,
                "A",
                "VIP"
        )).thenReturn(List.of(seat1, seat2));

        ScheduleSeatListResponse response =
                seatService.getSeatList(
                        eventSessionId,
                        "A",
                        "VIP"
                );

        assertThat(response).isNotNull();
        verify(scheduleSeatRepository)
                .findSeats(
                        eventSessionId,
                        "A",
                        "VIP"
                );
    }
}
