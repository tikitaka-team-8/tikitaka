package com.tikitaka.paymentnotification.notification.application.command;

import lombok.Getter;
import org.springframework.data.domain.Pageable;

@Getter
public class SearchNotificationsCommand {

    private final Long loginUserId;
    private final String userRole;
    private final String notificationType;
    private final String readStatus;
    private final Pageable pageable;

    public SearchNotificationsCommand(Long loginUserId, String userRole, String notificationType, String readStatus, Pageable pageable) {
        this.loginUserId = loginUserId;
        this.userRole = userRole;
        this.notificationType = notificationType;
        this.readStatus = readStatus;
        this.pageable = pageable;
    }
}
