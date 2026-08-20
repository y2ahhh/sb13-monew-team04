package com.codeit.sb13.monew.global.exception.notification;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;
import java.util.UUID;

public class NotificationOwnerMismatchException extends NotificationException{

    public NotificationOwnerMismatchException(UUID notificationId) {
        super(ApiErrorCode.NOTIFICATION_NOT_FOUND, Map.of("notificationId", notificationId));
    }
}
