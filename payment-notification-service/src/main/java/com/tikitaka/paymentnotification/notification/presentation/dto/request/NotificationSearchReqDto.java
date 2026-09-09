package com.tikitaka.paymentnotification.notification.presentation.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationSearchReqDto {

    private String notificationType;
    private String readStatus;
}
