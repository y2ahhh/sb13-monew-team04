package com.codeit.sb13.monew.activity.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityVisibilityUpdaterTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private JPAQueryFactory queryFactory;

    @Test
    @DisplayName("articleViewTargetCondition은 COMMENT 시 IllegalStateException을 던진다")
    void articleViewTargetConditionThrowsForCommentCause() {
        ActivityVisibilityUpdater updater = new ActivityVisibilityUpdater(entityManager, queryFactory);
        ActivityDeletionTarget target = ActivityDeletionTarget.deletedComment(UUID.randomUUID());

        assertThatThrownBy(() -> updater.articleViewTargetCondition(target))
                .isInstanceOf(IllegalStateException.class);
    }
}
