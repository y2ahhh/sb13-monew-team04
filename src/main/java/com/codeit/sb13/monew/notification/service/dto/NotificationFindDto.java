package com.codeit.sb13.monew.notification.service.dto;

import com.codeit.sb13.monew.global.exception.notification.NotificationInvalidCursorException;
import com.codeit.sb13.monew.global.exception.notification.NotificationInvalidLimitException;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationFindDto(
        UUID cursorId,
        LocalDateTime after,
        int limit,
        UUID userId
) {
    private static final int MAX_LIMIT = 100;

    public static NotificationFindDto of(String cursor, LocalDateTime after, int limit, UUID userId) {
        validateCursorCondition(cursor, after);
        validateLimit(limit);

        return new NotificationFindDto(
                parseCursorId(cursor),
                after,
                limit,
                userId
        );
    }

    private static void validateCursorCondition(String cursor, LocalDateTime after) {
        boolean hasCursor = cursor != null;
        boolean hasAfter = after != null;

        if (hasCursor != hasAfter) {
            throw new NotificationInvalidCursorException(cursor);
        }
    }

    private static void validateLimit(int limit) {
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new NotificationInvalidLimitException(limit);
        }
    }

    private static UUID parseCursorId(String cursor) {
        if (cursor == null) {
            return null;
        }

        try {
            return UUID.fromString(cursor);
        } catch (IllegalArgumentException e) {
            throw new NotificationInvalidCursorException(cursor);
        }
    }
}