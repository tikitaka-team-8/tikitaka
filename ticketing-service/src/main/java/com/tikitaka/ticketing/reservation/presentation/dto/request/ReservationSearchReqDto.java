package com.tikitaka.ticketing.reservation.presentation.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReservationSearchReqDto {

    @Size(max = 200)
    private String eventTitle;

    @Pattern(regexp = "PAYMENT_PENDING|PAYMENT_PROCESSING|CONFIRMED|FAILED|CANCEL_PENDING|CANCELLED")
    private String reservationStatus;

    @AssertTrue
    public boolean isSingleSearchCondition() {
        boolean hasEventTitle = eventTitle != null && !eventTitle.isBlank();
        boolean hasReservationStatus = reservationStatus != null && !reservationStatus.isBlank();
        return !(hasEventTitle && hasReservationStatus);
    }
}
