package com.codeit.sb13.monew.comment.repository;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.dto.RecentCommentActivityProjection;
import com.codeit.sb13.monew.global.config.JpaAuditingConfig;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class CommentRepositoryTest {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private TestEntityManager em;

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
    @DisplayName("사용자가 작성한 댓글을 최신 작성순으로 반환")
    void returns_user_comments_ordered_by_created_at_desc() throws InterruptedException {
        // given
        User targetUser = new User("test@eamil.com", "testNickname", "testPassword");
        User otherUser = new User("otherTest@email.com", "otherTestNickname", "otherTestPassword");
        userRepository.saveAndFlush(targetUser);
        userRepository.saveAndFlush(otherUser);

        Article article = articleRepository.saveAndFlush(new Article("testTitle", "testContent", "link", LocalDateTime.now(), "source"));
        commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment1"));
        Thread.sleep(100);
        commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment2"));
        Thread.sleep(100);
        commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment3"));
        commentRepository.saveAndFlush(new Comment(article.getId(), otherUser, "testComment4"));

        em.clear();
        // when
        List<RecentCommentActivityProjection> recentCommentActivities = commentRepository.findRecentCommentActivities(targetUser.getId(), PageRequest.of(0, 10));


        recentCommentActivities.forEach(System.out::println);
        // then
        assertThat(recentCommentActivities)
                .extracting(RecentCommentActivityProjection::content)
                .containsExactly("testComment3", "testComment2", "testComment1")
                .doesNotContain("testComment4");

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
