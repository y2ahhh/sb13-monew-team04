package com.codeit.sb13.monew.activity.service;

import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.ARTICLE_DELETED;
import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.COMMENT_DELETED;
import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.USER_DELETED;

import com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus;

/**
 * 삭제 원인과 해당 원인이 활동 row에 반영할 노출 상태를 연결한다.
 */
enum ActivityDeletionCause {
    ARTICLE(ARTICLE_DELETED),
    COMMENT(COMMENT_DELETED),
    USER(USER_DELETED);

    private final ActivityVisibilityStatus visibilityStatus;

    ActivityDeletionCause(ActivityVisibilityStatus visibilityStatus) {
        this.visibilityStatus = visibilityStatus;
    }

    /**
     * 삭제 원인에 대응하는 활동 row의 노출 상태를 반환한다.
     *
     * @return 삭제 원인별 노출 상태
     */
    ActivityVisibilityStatus visibilityStatus() {
        return visibilityStatus;
    }
}
