package com.tikitaka.ticketing.reservation.infrastructure.adapter;

import com.tikitaka.ticketing.global.exception.BusinessException;
import com.tikitaka.ticketing.global.exception.CommonErrorCode;
import com.tikitaka.ticketing.reservation.domain.model.ReservationEventSessionInfo;
import com.tikitaka.ticketing.reservation.domain.port.EventSessionQueryPort;
import com.tikitaka.ticketing.reservation.infrastructure.client.PlatformEventSessionClient;
import feign.FeignException;
import feign.RetryableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EventSessionQueryAdapter implements EventSessionQueryPort {

    private final PlatformEventSessionClient platformEventSessionClient;
    private final String internalServiceKey;

    public EventSessionQueryAdapter(PlatformEventSessionClient platformEventSessionClient, @Value("${INTERNAL_SERVICE_KEY}") String internalServiceKey) {
        this.platformEventSessionClient = platformEventSessionClient;
        this.internalServiceKey = internalServiceKey;
    }

    @Override
    public ReservationEventSessionInfo getReservationInfo(UUID eventSessionId) {
        try {
            return platformEventSessionClient.getReservationInfo(eventSessionId, internalServiceKey);
        } catch (RetryableException exception) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_SERVICE_TIMEOUT);
        } catch (FeignException exception) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_SERVICE_FAILURE);
        }
    }
}
