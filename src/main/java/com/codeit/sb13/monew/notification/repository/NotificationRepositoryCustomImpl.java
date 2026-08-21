package com.codeit.sb13.monew.notification.repository;

import com.codeit.sb13.monew.notification.domain.Notification;
import com.codeit.sb13.monew.notification.domain.QNotification;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class NotificationRepositoryCustomImpl implements NotificationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Notification> findUnconfirmedByUserWithCursor(UUID userId, UUID cursorId, LocalDateTime after, int limit) {
        QNotification notification = QNotification.notification;

        BooleanBuilder condition = new BooleanBuilder()
                .and(notification.user.id.eq(userId))
                .and(notification.confirmed.isFalse());

        if (cursorId != null && after != null) {
            condition.and(notification.createdAt.lt(after)
                            .or(notification.createdAt.eq(after).and(notification.id.lt(cursorId)))
            );
        }

        return queryFactory
                .selectFrom(notification)
                .where(condition)
                .orderBy(notification.createdAt.desc(), notification.id.desc())
                .limit(limit)
                .fetch();
    }
}
