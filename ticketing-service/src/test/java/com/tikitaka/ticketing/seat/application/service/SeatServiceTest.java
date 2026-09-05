package com.tikitaka.ticketing.seat.application.service;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.queue.application.QueueAdmissionValidator;
import com.tikitaka.ticketing.queue.application.QueueService;
import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import com.tikitaka.ticketing.seat.domain.entity.SeatHold;
import com.tikitaka.ticketing.seat.domain.enums.HoldStatus;
import com.tikitaka.ticketing.seat.domain.enums.ReleaseReason;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
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
    private UUID seatHoldId;
    private Long userId;
    private String admissionToken;
    private String idempotencyKey;



    @BeforeEach
    void setUp() {
        eventSessionId = UUID.randomUUID();
        scheduleSeatId = UUID.randomUUID();
        seatHoldId = UUID.randomUUID();
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

    @Test
    void 선점을_정상적으로_취소한다() {
        // given
        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        ScheduleSeat seat = mock(ScheduleSeat.class);
        Instant releasedAt = Instant.parse("2026-09-04T03:05:00Z");

        given(seatHoldRepository.findById(seatHoldId)).willReturn(Optional.of(seatHold));
        given(scheduleSeatRepository.findByIdForUpdate(scheduleSeatId)).willReturn(Optional.of(seat));
        given(clock.instant()).willReturn(releasedAt);

        // when
        seatService.cancelHold(seatHoldId, userId);

        // then
        assertThat(seatHold.getHoldStatus()).isEqualTo(HoldStatus.RELEASED);
        assertThat(seatHold.getReleasedAt()).isEqualTo(releasedAt);
        assertThat(seatHold.getReleaseReason()).isEqualTo(ReleaseReason.USER_CANCEL);
        then(seat).should().release();
    }

    @Test
    void 존재하지_않는_선점을_취소하려하면_예외가_발생한다() {

        given(seatHoldRepository.findById(seatHoldId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> seatService.cancelHold(seatHoldId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.SEAT_HOLD_NOT_FOUND);
    }

    @Test
    void 소유자가_다르면_선점_취소시_예외가_발생한다() {

        SeatHold seatHold = SeatHold.hold(
                999L, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        given(seatHoldRepository.findById(seatHoldId)).willReturn(Optional.of(seatHold));

        assertThatThrownBy(() -> seatService.cancelHold(seatHoldId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.SEAT_HOLD_OWNERSHIP_REQUIRED);

        then(scheduleSeatRepository).should(never()).findByIdForUpdate(any(UUID.class));
    }

    @Test
    void 이미_RELEASED된_선점을_취소하면_아무_처리_없이_성공한다() {

        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        seatHold.release(ReleaseReason.USER_CANCEL, Instant.parse("2026-09-04T03:01:00Z"));

        given(seatHoldRepository.findById(seatHoldId)).willReturn(Optional.of(seatHold));

        seatService.cancelHold(seatHoldId, userId);

        then(scheduleSeatRepository).should(never()).findByIdForUpdate(any(UUID.class));
    }

    @Test
    void CONFIRMED_상태의_선점을_취소하려하면_예외가_발생한다() {

        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        ReflectionTestUtils.setField(seatHold, "holdStatus", HoldStatus.CONFIRMED);
        ScheduleSeat seat = mock(ScheduleSeat.class);

        given(seatHoldRepository.findById(seatHoldId)).willReturn(Optional.of(seatHold));
        given(scheduleSeatRepository.findByIdForUpdate(scheduleSeatId)).willReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatService.cancelHold(seatHoldId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.SEAT_HOLD_ALREADY_CLOSED);

        then(seat).should(never()).release();
    }

    @Test
    void HOLDING_상태의_선점을_결제를_위해_연장한다() {

        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        Instant now = Instant.parse("2026-09-04T03:05:00Z");

        given(seatHoldRepository.findById(seatHoldId)).willReturn(Optional.of(seatHold));
        given(clock.instant()).willReturn(now);

        seatService.validateAndExtend(seatHoldId);

        assertThat(seatHold.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(10)));
        assertThat(seatHold.getHoldStatus()).isEqualTo(HoldStatus.HOLDING);
    }

    @Test
    void 존재하지_않는_선점은_연장할_수_없다() {

        given(seatHoldRepository.findById(seatHoldId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> seatService.validateAndExtend(seatHoldId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.SEAT_HOLD_NOT_FOUND);
    }

    @Test
    void HOLDING이_아닌_선점은_연장할_수_없다() {

        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        ReflectionTestUtils.setField(seatHold, "holdStatus", HoldStatus.CONFIRMED);

        given(seatHoldRepository.findById(seatHoldId)).willReturn(Optional.of(seatHold));

        assertThatThrownBy(() -> seatService.validateAndExtend(seatHoldId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.SEAT_STATUS_CONFLICT);
    }

    @Test
    void 이미_연장된_선점은_다시_연장을_요청해도_그대로_유지된다() {

        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        Instant firstExtendAt = Instant.parse("2026-09-04T03:01:00Z");
        seatHold.extendExpiry(firstExtendAt, Duration.ofMinutes(10));
        Instant expiresAfterFirstExtension = seatHold.getExpiresAt();

        given(seatHoldRepository.findById(seatHoldId)).willReturn(Optional.of(seatHold));
        given(clock.instant()).willReturn(Instant.parse("2026-09-04T03:05:00Z"));

        seatService.validateAndExtend(seatHoldId);

        assertThat(seatHold.getExpiresAt()).isEqualTo(expiresAfterFirstExtension);
    }

}