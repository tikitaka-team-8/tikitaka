package com.tikitaka.ticketing.seat.application.service;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.queue.application.QueueAdmissionValidator;
import com.tikitaka.ticketing.queue.application.QueueService;
import com.tikitaka.ticketing.seat.application.command.CreateScheduleSeatsCommand;
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

        given(seatHoldRepository.findByIdForUpdate(seatHoldId)).willReturn(Optional.of(seatHold));
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

        given(seatHoldRepository.findByIdForUpdate(seatHoldId)).willReturn(Optional.empty());

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
        given(seatHoldRepository.findByIdForUpdate(seatHoldId)).willReturn(Optional.of(seatHold));

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

        given(seatHoldRepository.findByIdForUpdate(seatHoldId)).willReturn(Optional.of(seatHold));

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

        given(seatHoldRepository.findByIdForUpdate(seatHoldId)).willReturn(Optional.of(seatHold));
        given(scheduleSeatRepository.findByIdForUpdate(scheduleSeatId)).willReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatService.cancelHold(seatHoldId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.SEAT_HOLD_ALREADY_CLOSED);

        then(seat).should(never()).release();
    }

    @Test
    void HOLDING_상태의_선점은_결제_처리_권한을_원자적으로_획득한다() {

        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        Instant now = Instant.parse("2026-09-04T03:05:00Z");

        given(seatHoldRepository.findByIdForUpdate(seatHoldId)).willReturn(Optional.of(seatHold));
        given(clock.instant()).willReturn(now);

        seatService.validateAndExtend(seatHoldId);

        assertThat(seatHold.getHoldStatus()).isEqualTo(HoldStatus.RESERVED);
        assertThat(seatHold.getReservedAt()).isEqualTo(now);
    }

    @Test
    void 존재하지_않는_선점은_결제_처리_권한을_획득할_수_없다() {

        given(seatHoldRepository.findByIdForUpdate(seatHoldId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> seatService.validateAndExtend(seatHoldId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.SEAT_HOLD_NOT_FOUND);
    }

    @Test
    void HOLDING이_아닌_선점은_결제_처리_권한을_획득할_수_없다() {

        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        ReflectionTestUtils.setField(seatHold, "holdStatus", HoldStatus.CONFIRMED);

        given(seatHoldRepository.findByIdForUpdate(seatHoldId)).willReturn(Optional.of(seatHold));
        given(clock.instant()).willReturn(Instant.parse("2026-09-04T03:05:00Z"));

        assertThatThrownBy(() -> seatService.validateAndExtend(seatHoldId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.SEAT_HOLD_ALREADY_CLOSED);
    }

    @Test
    void 만료_시각이_지난_선점은_결제_처리_권한을_획득할_수_없다() {

        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        Instant afterExpiry = Instant.parse("2026-09-04T03:11:00Z");

        given(seatHoldRepository.findByIdForUpdate(seatHoldId)).willReturn(Optional.of(seatHold));
        given(clock.instant()).willReturn(afterExpiry);

        assertThatThrownBy(() -> seatService.validateAndExtend(seatHoldId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.SEAT_HOLD_ALREADY_CLOSED);

        assertThat(seatHold.getHoldStatus()).isEqualTo(HoldStatus.HOLDING);
    }


    @Test
    void 만료된_HOLDING_선점의_id_목록을_조회한다() {

        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        Instant now = Instant.parse("2026-09-04T03:20:00Z");

        given(clock.instant()).willReturn(now);
        given(seatHoldRepository.findExpiredHolds(now, 100))
                .willReturn(List.of(seatHold));

        List<UUID> result = seatService.findOverdueHoldIds(100);

        assertThat(result).containsExactly(seatHold.getSeatHoldId());
    }

    @Test
    void RESERVED_상태의_선점을_확정하면_판매완료로_전이된다() {

        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        seatHold.reserve(Instant.parse("2026-09-04T03:02:00Z"));
        ScheduleSeat seat = mock(ScheduleSeat.class);
        Instant confirmedAt = Instant.parse("2026-09-04T03:05:00Z");

        given(seatHoldRepository.findByIdForUpdate(seatHoldId)).willReturn(Optional.of(seatHold));
        given(scheduleSeatRepository.findByIdForUpdate(scheduleSeatId)).willReturn(Optional.of(seat));
        given(clock.instant()).willReturn(confirmedAt);

        seatService.confirmHold(seatHoldId);

        assertThat(seatHold.getHoldStatus()).isEqualTo(HoldStatus.CONFIRMED);
        then(seat).should().sell();
    }

    @Test
    void 이미_CONFIRMED된_선점을_다시_확정해도_아무_처리_없이_성공한다() {

        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        seatHold.reserve(Instant.parse("2026-09-04T03:02:00Z"));
        seatHold.confirm(Instant.parse("2026-09-04T03:05:00Z"));

        given(seatHoldRepository.findByIdForUpdate(seatHoldId)).willReturn(Optional.of(seatHold));

        seatService.confirmHold(seatHoldId);

        then(scheduleSeatRepository).should(never()).findByIdForUpdate(any());
    }

    @Test
    void RELEASED된_선점을_확정하려하면_예외가_발생한다() {

        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        seatHold.release(ReleaseReason.USER_CANCEL, Instant.parse("2026-09-04T03:01:00Z"));
        ScheduleSeat seat = mock(ScheduleSeat.class);

        given(seatHoldRepository.findByIdForUpdate(seatHoldId)).willReturn(Optional.of(seatHold));
        given(scheduleSeatRepository.findByIdForUpdate(scheduleSeatId)).willReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatService.confirmHold(seatHoldId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.SEAT_HOLD_ALREADY_CLOSED);
    }

    @Test
    void 결제_실패시_HOLDING_상태의_선점을_해제하면_좌석도_판매가능_상태로_돌아간다() {

        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        ScheduleSeat seat = mock(ScheduleSeat.class);
        Instant releasedAt = Instant.parse("2026-09-04T03:05:00Z");

        given(seatHoldRepository.findByIdForUpdate(seatHoldId)).willReturn(Optional.of(seatHold));
        given(scheduleSeatRepository.findByIdForUpdate(scheduleSeatId)).willReturn(Optional.of(seat));
        given(clock.instant()).willReturn(releasedAt);

        seatService.releaseHold(seatHoldId, ReleaseReason.PAYMENT_FAILED);

        assertThat(seatHold.getHoldStatus()).isEqualTo(HoldStatus.RELEASED);
        assertThat(seatHold.getReleaseReason()).isEqualTo(ReleaseReason.PAYMENT_FAILED);
        then(seat).should().release();
    }

    @Test
    void 이미_RELEASED된_선점을_다시_해제해도_아무_처리_없이_성공한다() {

        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        seatHold.release(ReleaseReason.PAYMENT_FAILED, Instant.parse("2026-09-04T03:01:00Z"));

        given(seatHoldRepository.findByIdForUpdate(seatHoldId)).willReturn(Optional.of(seatHold));

        seatService.releaseHold(seatHoldId, ReleaseReason.RESERVATION_CANCELED);

        then(scheduleSeatRepository).should(never()).findByIdForUpdate(any());
    }

    @Test
    void CONFIRMED된_선점을_해제하려하면_예외가_발생한다() {

        SeatHold seatHold = SeatHold.hold(
                userId, scheduleSeatId, idempotencyKey,
                Instant.parse("2026-09-04T03:00:00Z"),
                Instant.parse("2026-09-04T03:10:00Z")
        );
        seatHold.confirm(Instant.parse("2026-09-04T03:01:00Z"));
        ScheduleSeat seat = mock(ScheduleSeat.class);

        given(seatHoldRepository.findByIdForUpdate(seatHoldId)).willReturn(Optional.of(seatHold));
        given(scheduleSeatRepository.findByIdForUpdate(scheduleSeatId)).willReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatService.releaseHold(seatHoldId, ReleaseReason.PAYMENT_FAILED))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.SEAT_HOLD_ALREADY_CLOSED);
    }


    @Test
    void 회차별_좌석_재고_생성_요청에_동일한_venueSeatId가_중복되면_예외가_발생한다() {
        UUID duplicatedVenueSeatId = UUID.randomUUID();
        CreateScheduleSeatsCommand command = new CreateScheduleSeatsCommand(
                eventSessionId,
                UUID.randomUUID(),
                List.of(
                        new CreateScheduleSeatsCommand.SeatItem(
                                duplicatedVenueSeatId, "A", "1", "1", "VIP", 10000L
                        ),
                        new CreateScheduleSeatsCommand.SeatItem(
                                duplicatedVenueSeatId, "A", "1", "2", "VIP", 10000L
                        )
                )
        );

        assertThatThrownBy(() -> seatService.createScheduleSeats(command))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SeatErrorCode.INVALID_INPUT);

        verifyNoInteractions(scheduleSeatRepository);
    }

}