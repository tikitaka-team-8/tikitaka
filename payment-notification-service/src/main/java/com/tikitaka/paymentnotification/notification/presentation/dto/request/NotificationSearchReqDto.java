package com.tikitaka.paymentnotification.notification.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "알림 목록 조회 조건")
public class NotificationSearchReqDto {

    private String notificationType;
    private String readStatus;
}
