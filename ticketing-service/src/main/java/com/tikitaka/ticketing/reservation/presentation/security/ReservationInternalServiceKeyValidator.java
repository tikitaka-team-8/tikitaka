package com.tikitaka.ticketing.reservation.presentation.security;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.reservation.exception.ReservationErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ReservationInternalServiceKeyValidator {
    // TODO: 추후 Ticketing Service 내 공통 필터 방식으로 변경 적용 검토
    private final byte[] expectedServiceKey;

    public ReservationInternalServiceKeyValidator(ReservationInternalServiceKeyProperties properties) {
        this.expectedServiceKey = properties.key().getBytes(StandardCharsets.UTF_8);
    }

    public void validate(String actualServiceKey) {
        if (actualServiceKey == null || !MessageDigest.isEqual(
                expectedServiceKey,
                actualServiceKey.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new BusinessException(ReservationErrorCode.INTERNAL_SERVICE_AUTHENTICATION_FAILED);
        }
    }
}
