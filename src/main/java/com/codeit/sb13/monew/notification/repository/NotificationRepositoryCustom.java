package com.codeit.sb13.monew.notification.repository;

import com.codeit.sb13.monew.notification.domain.Notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepositoryCustom {

    List<Notification> findUnconfirmedByUserWithCursor(UUID userId, UUID cursorId, LocalDateTime after, int limit);
}
