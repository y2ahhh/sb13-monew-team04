package com.codeit.sb13.monew.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import com.codeit.sb13.monew.comment.entity.Comment;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@DisplayName("댓글 서비스 - TDD")
public class CommentServiceTest {

  @Mock
  CommentRepository commentRepository;

  @Mock
  private CommentDto commentDto;

  @InjectMocks
  private CommentService commentService;

  @Test
  @DisplayName("댓글 생성 성공 - RED")
  void 댓글_생성_성공() {
    // given
    UUID articleId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    CommentRegisterRequest request=new CommentCreateRequest(articleId, userId, "테스트 댓글");
    given(commentRepository.save(any(Comment.class))).willAnswer(invocation -> invocation.getArgument(0));
    // when

    CommentDto result = commentService.create(request);
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    // then
    then(commentRepository).should().save(captor.capture());
    Comment savedComment = captor.getValue();
    assertThat(savedComment.content).isEqualTo("테스트 댓글");
    assertThat(result).isNotNull();
  }
}
