package com.codeit.sb13.monew.comment.repository;

import com.codeit.sb13.monew.comment.repository.dto.RecentCommentActivityProjection;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CommentRepositoryTest {

    @Autowired
    private CommentRepository commentRepository;

    @Test
    @DisplayName("작성한 댓글이 없으면 빈 목록 반환")
    void returns_empty_list_when_user_has_no_comments() {

        // given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        // when
        List<RecentCommentActivityProjection> projections = commentRepository.findRecentCommentActivities(userId, pageable);


        // then
        assertThat(projections).isEmpty();

    }

    @Test
    @Disabled("TODO")
    @DisplayName("여러 댓글은 createdAt DESC 최신순")
    void 여러_댓글은_createdAt_DESC_최신순() {
        // given

        // when


        // then


    }

    @Test
    @Disabled("TODO")
    @DisplayName("10건 초과 시 최신 10건만 반환")
    void _10건_초과_시_최신_10건만_반환() {
        // given

        // when


        // then


    }

    @Test
    @Disabled("TODO")
    @DisplayName("삭제된 사용자 댓글 제외")
    void 삭제된_사용자_댓글_제외() {
        // given

        // when


        // then


    }

    @Test
    @Disabled("TODO")
    @DisplayName("삭제된 댓글 제외")
    void 삭제된_댓글_제외() {
        // given

        // when


        // then


    }

    @Test
    @Disabled("TODO")
    @DisplayName("삭제된 기사 댓글 제외")
    void 삭제된_기사_댓글_제외() {
        // given

        // when


        // then


    }

}
