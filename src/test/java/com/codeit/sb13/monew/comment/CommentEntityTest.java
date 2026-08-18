package com.codeit.sb13.monew.comment;

import com.codeit.sb13.monew.comment.domain.Comment;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("댓글 - TDD")
public class CommentEntityTest {

  @Test
  @DisplayName("댓글 생성 성공 - GREEN")
  void 댓글_생성_성공() {
    // given
    UUID articleId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    Comment comment=new Comment(articleId, userId, "테스트 댓글");
    // then
    Assertions.assertAll(
        ()->Assertions.assertEquals(articleId, comment.getArticleId()),
        ()->Assertions.assertEquals(userId, comment.getUserId()),
        ()->Assertions.assertEquals("테스트 댓글", comment.getContent()),
        ()->Assertions.assertNotNull(comment.getCreatedAt())
    );
  }
}
