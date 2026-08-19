package com.codeit.sb13.monew.global.exception.notification;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;
import java.util.UUID;

public class NotificationOwnerMismatchException extends NotificationException{

    public NotificationOwnerMismatchException(UUID notificationId, UUID userId) {
        super(ApiErrorCode.NOTIFICATION_OWNER_MISMATCH, Map.of("notificationId", notificationId, "userId", userId));
    }
}
