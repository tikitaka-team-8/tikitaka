package com.tikitaka.ticketing.seat.application.service;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.queue.application.QueueAdmissionValidator;
import com.tikitaka.ticketing.queue.application.QueueService;
import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import com.tikitaka.ticketing.seat.domain.entity.SeatHold;
import com.tikitaka.ticketing.seat.domain.enums.SeatStatus;
import com.tikitaka.ticketing.seat.domain.repository.ScheduleSeatRepository;
import com.tikitaka.ticketing.seat.domain.repository.SeatHoldRepository;
import com.tikitaka.ticketing.seat.exception.SeatErrorCode;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatListResponse;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock
    private ScheduleSeatRepository scheduleSeatRepository;

    @Mock
    private SeatHoldRepository seatHoldRepository;

    @Mock
    private  QueueAdmissionValidator queueAdmissionValidator;

    @Mock
    private Clock clock;

    @InjectMocks
    private SeatService seatService;

    private UUID eventSessionId;
    private UUID scheduleSeatId;
    private Long userId;
    private String admissionToken;
    private String idempotencyKey;



    @BeforeEach
    void setUp() {
        eventSessionId = UUID.randomUUID();
        scheduleSeatId = UUID.randomUUID();
        userId = 1L;
        admissionToken = "admission-token";
        idempotencyKey = "test-idempotency-key";
    }

    @Test
    void 회차_좌석_목록을_조회한다() {

        ScheduleSeat seat1 = mock(ScheduleSeat.class);
        ScheduleSeat seat2 = mock(ScheduleSeat.class);
        ScheduleSeat seat3 = mock(ScheduleSeat.class);

        List<ScheduleSeat> seats =
                List.of(seat1, seat2, seat3);

        doNothing().when(queueAdmissionValidator)
                .validateAndEnter(
                        eventSessionId,
                        userId,
                        admissionToken
                );

        when(scheduleSeatRepository.findSeats(
                eventSessionId,
                null,
                null
        )).thenReturn(seats);

        ScheduleSeatListResponse response =
                seatService.getSeatList(
                        eventSessionId,
                        null,
                        null,
                        userId,
                        admissionToken
                );

        assertThat(response).isNotNull();

        verify(queueAdmissionValidator)
                .validateAndEnter(
                        eventSessionId,
                        userId,
                        admissionToken
                );

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
                        "VIP",
                        userId,
                        admissionToken
                );

        assertThat(response).isNotNull();

        verify(queueAdmissionValidator)
                .validateAndEnter(
                        eventSessionId,
                        userId,
                        admissionToken
                );

        verify(scheduleSeatRepository)
                .findSeats(
                        eventSessionId,
                        "A",
                        "VIP"
                );
    }

    @Test
    void 좌석_상세를_조회한다() {

        ScheduleSeat seat = mock(ScheduleSeat.class);

        when(scheduleSeatRepository.findSeatDetail(
                eventSessionId,
                scheduleSeatId
        )).thenReturn(Optional.of(seat));

        ScheduleSeatResponse response =
                seatService.getSeatDetail(
                        eventSessionId,
                        scheduleSeatId,
                        userId
                );

        assertThat(response).isNotNull();
        verify(queueAdmissionValidator)
                .validateEntered(
                        eventSessionId,
                        userId
                );
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
                        scheduleSeatId,
                        userId
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode()
                )
                .isEqualTo(SeatErrorCode.SESSION_OR_SEAT_NOT_FOUND);
        verify(queueAdmissionValidator)
                .validateEntered(
                        eventSessionId,
                        userId
                );
        verify(scheduleSeatRepository)
                .findSeatDetail(
                        eventSessionId,
                        scheduleSeatId
                );
    }

    @Test
    void 대기열_검증에_실패하면_좌석을_조회하지_않는다() {

        doThrow(new BusinessException(SeatErrorCode.SESSION_OR_SEAT_NOT_FOUND))
                .when(queueAdmissionValidator)
                .validateAndEnter(
                        eventSessionId,
                        userId,
                        admissionToken
                );
        assertThatThrownBy(() ->
                seatService.getSeatList(
                        eventSessionId,
                        null,
                        null,
                        userId,
                        admissionToken
                )
        )
                .isInstanceOf(BusinessException.class);
        verify(queueAdmissionValidator)
                .validateAndEnter(
                        eventSessionId,
                        userId,
                        admissionToken
                );
        verify(scheduleSeatRepository, never())
                .findSeats(
                        eventSessionId,
                        null,
                        null
                );
    }

    @Test
    void 좌석_선점시_비관적락_조회_메서드를_호출한다() {
        // given
        ScheduleSeat seat = mock(ScheduleSeat.class);
        SeatHold savedHold = mock(SeatHold.class);

        Instant heldAt = Instant.parse("2026-09-04T03:00:00Z");

        given(seatHoldRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).willReturn(Optional.empty());

        given(scheduleSeatRepository.findByIdForUpdate(
                eventSessionId,
                scheduleSeatId
        )).willReturn(Optional.of(seat));

        given(seat.getScheduleSeatId())
                .willReturn(scheduleSeatId);

        given(clock.instant())
                .willReturn(heldAt);

        given(seatHoldRepository.save(any(SeatHold.class)))
                .willReturn(savedHold);

        // when
        seatService.holdSeat(
                eventSessionId,
                scheduleSeatId,
                userId,
                idempotencyKey
        );

        // then
        then(scheduleSeatRepository).should()
                .findByIdForUpdate(
                        eventSessionId,
                        scheduleSeatId
                );
    }
}