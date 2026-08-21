package com.codeit.sb13.monew.notification.service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationFindDto(
        String cursor,
        LocalDateTime after,
        int limit,
        UUID userId
) {
}
