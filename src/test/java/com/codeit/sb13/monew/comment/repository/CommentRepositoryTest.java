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
    void returns_user_comments_ordered_by_created_at_desc() {
        // given
        User targetUser = new User("test@eamil.com", "testNickname", "testPassword");
        User otherUser = new User("otherTest@email.com", "otherTestNickname", "otherTestPassword");
        userRepository.saveAndFlush(targetUser);
        userRepository.saveAndFlush(otherUser);

        Article article = articleRepository.saveAndFlush(new Article("testTitle", "testContent", "link", LocalDateTime.now(), "source"));
        Comment oldestComment = commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment1"));
        Comment middleComment = commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment2"));
        Comment newestComment = commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment3"));
        commentRepository.saveAndFlush(new Comment(article.getId(), otherUser, "testComment4"));

        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 10, 0);
        updateCommentCreatedAt(oldestComment.getId(), baseTime.minusMinutes(2));
        updateCommentCreatedAt(middleComment.getId(), baseTime.minusMinutes(1));
        updateCommentCreatedAt(newestComment.getId(), baseTime);

        em.clear();

        // when
        List<RecentCommentActivityProjection> recentCommentActivities = commentRepository.findRecentCommentActivities(targetUser.getId(), PageRequest.of(0, 10));

        // then
        assertThat(recentCommentActivities)
                .extracting(RecentCommentActivityProjection::content)
                .containsExactly("testComment3", "testComment2", "testComment1");
    }

    @Test
    @DisplayName("10건 초과 시 최신 10건만 반환")
    void _10건_초과_시_최신_10건만_반환() {
        // given
        User targetUser = new User("test@eamil.com", "testNickname", "testPassword");
        User otherUser = new User("otherTest@email.com", "otherTestNickname", "otherTestPassword");
        userRepository.saveAndFlush(targetUser);
        userRepository.saveAndFlush(otherUser);

        Article article = articleRepository.saveAndFlush(new Article("testTitle", "testContent", "link", LocalDateTime.now(), "source"));
        Comment firstComment = commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment1"));
        Comment secondComment = commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment2"));
        Comment thirdComment = commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment3"));
        Comment fourComment = commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment4"));
        Comment fiveComment = commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment5"));
        Comment sixComment = commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment6"));
        Comment sevenComment = commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment7"));
        Comment eightComment = commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment8"));
        Comment nineComment = commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment9"));
        Comment tenComment = commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment10"));
        Comment elevenComment = commentRepository.saveAndFlush(new Comment(article.getId(), targetUser, "testComment11"));
        commentRepository.saveAndFlush(new Comment(article.getId(), otherUser, "otherTestComment"));

        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 10, 0);
        updateCommentCreatedAt(firstComment.getId(), baseTime.minusMinutes(10));
        updateCommentCreatedAt(secondComment.getId(), baseTime.minusMinutes(9));
        updateCommentCreatedAt(thirdComment.getId(), baseTime.minusMinutes(8));
        updateCommentCreatedAt(fourComment.getId(), baseTime.minusMinutes(7));
        updateCommentCreatedAt(fiveComment.getId(), baseTime.minusMinutes(6));
        updateCommentCreatedAt(sixComment.getId(), baseTime.minusMinutes(5));
        updateCommentCreatedAt(sevenComment.getId(), baseTime.minusMinutes(4));
        updateCommentCreatedAt(eightComment.getId(), baseTime.minusMinutes(3));
        updateCommentCreatedAt(nineComment.getId(), baseTime.minusMinutes(2));
        updateCommentCreatedAt(tenComment.getId(), baseTime.minusMinutes(1));
        updateCommentCreatedAt(elevenComment.getId(), baseTime);

        em.clear();

        // when
        List<RecentCommentActivityProjection> recentCommentActivities = commentRepository.findRecentCommentActivities(targetUser.getId(), PageRequest.of(0, 10));

        // then
        assertThat(recentCommentActivities)
                .extracting(RecentCommentActivityProjection::content)
                .containsExactly("testComment11", "testComment10", "testComment9", "testComment8", "testComment7", "testComment6", "testComment5", "testComment4", "testComment3", "testComment2");


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

    private void updateCommentCreatedAt(UUID commentId, LocalDateTime createdAt) {
        em.getEntityManager()
                .createNativeQuery("UPDATE comments SET created_at = ? WHERE id = ?")
                .setParameter(1, createdAt)
                .setParameter(2, commentId)
                .executeUpdate();
    }


}
