package com.tikitaka.ticketing.reservation.presentation.controller;

import com.tikitaka.ticketing.global.response.ApiResponse;
import com.tikitaka.ticketing.global.response.PageMeta;
import com.tikitaka.ticketing.reservation.application.ReservationService;
import com.tikitaka.ticketing.reservation.application.command.GetReservationCommand;
import com.tikitaka.ticketing.reservation.application.command.SearchReservationsCommand;
import com.tikitaka.ticketing.reservation.application.result.ReservationResult;
import com.tikitaka.ticketing.reservation.application.result.ReservationSearchResult;
import com.tikitaka.ticketing.reservation.presentation.dto.request.ReservationSearchReqDto;
import com.tikitaka.ticketing.reservation.presentation.dto.response.ReservationResDto;
import com.tikitaka.ticketing.reservation.presentation.dto.response.ReservationSearchResDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {
    // TODO: 추후 gateway 쪽 헤더 상수 이름으로 직접 변경
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservationSearchResDto>>> searchReservations(
            @RequestHeader(USER_ID_HEADER) @Positive Long loginUserId,
            @RequestHeader(USER_ROLE_HEADER) @Pattern(regexp = "USER|ADMIN") String userRole,
            @Valid @ModelAttribute ReservationSearchReqDto requestDto,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        SearchReservationsCommand command = new SearchReservationsCommand(
                loginUserId, userRole, requestDto.getEventTitle(), requestDto.getReservationStatus(), pageable);

        Page<ReservationSearchResult> resultPage = reservationService.searchReservations(command);

        List<ReservationSearchResDto> response = resultPage.getContent().stream()
                .map(ReservationSearchResDto::new).toList();
        PageMeta meta = new PageMeta(
                resultPage.getNumber(), resultPage.getSize(), resultPage.getTotalElements(),
                resultPage.getTotalPages(), resultPage.hasNext()
        );

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK, "예매 목록 조회에 성공했습니다.", response, meta)
        );
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResDto>> getReservation(
            @RequestHeader(USER_ID_HEADER) @Positive Long loginUserId,
            @RequestHeader(USER_ROLE_HEADER) @Pattern(regexp = "USER|ADMIN") String userRole,
            @PathVariable UUID reservationId) {

        GetReservationCommand command = new GetReservationCommand(loginUserId, userRole, reservationId);
        ReservationResult result = reservationService.getReservation(command);

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK, "예매 상세 조회에 성공했습니다.", new ReservationResDto(result))
        );
    }
}
