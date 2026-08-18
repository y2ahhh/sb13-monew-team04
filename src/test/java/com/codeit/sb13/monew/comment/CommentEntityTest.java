package com.codeit.sb13.monew.comment;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("댓글 - TDD")
public class CommentEntityTest {

  @Mock
  private CommentRepository commentRepository;

  @Test
  @DisplayName("댓글 등록 실패 - RED")
  void 댓글_등록_실패() {
    // given
    UUID articleId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    Comment comment=new Comment(articleId, userId, "테스트 댓글");
    // when
    Comment savedComment = commentRepository.save(comment);
    // then
    assertThat(commentRepository).isNotNull();
  }
}
