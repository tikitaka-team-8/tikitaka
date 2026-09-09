package com.tikitaka.paymentnotification.notification.application.command;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ReadNotificationCommand {

    private final Long loginUserId;
    private final String userRole;
    private final UUID notificationId;

    public ReadNotificationCommand(Long loginUserId, String userRole, UUID notificationId) {
        this.loginUserId = loginUserId;
        this.userRole = userRole;
        this.notificationId = notificationId;
    }
}
