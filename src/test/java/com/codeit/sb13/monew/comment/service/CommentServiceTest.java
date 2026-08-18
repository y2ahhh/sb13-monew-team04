package com.codeit.sb13.monew.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.service.dto.CommentRegisterRequest;
import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.comment.service.impl.CommentServiceImpl;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("댓글 서비스 - TDD")
@ExtendWith(MockitoExtension.class)
public class CommentServiceTest {

  @Mock
  CommentRepository commentRepository;

  @InjectMocks
  private CommentServiceImpl commentService;

  @Test
  @DisplayName("댓글 생성 성공 - GREEN")
  void 댓글_생성_성공() {
    // given
    UUID articleId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();

    CommentRegisterRequest request=new CommentRegisterRequest(articleId, userId, "테스트 댓글");
    given(commentRepository.save(any(Comment.class))).willAnswer(invocation -> {
      Comment comment = invocation.getArgument(0);
      ReflectionTestUtils.setField(comment, "id", commentId);
      return comment;
    });
    // when

    CommentDto result = commentService.create(request);
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    // then
    then(commentRepository).should(times(1)).save(captor.capture());
    Comment savedComment = captor.getValue();
    Assertions.assertAll(
        () -> assertThat(savedComment.getArticleId()).isEqualTo(articleId),
        () -> assertThat(savedComment.getUserId()).isEqualTo(userId),
        () -> assertThat(savedComment.getContent()).isEqualTo("테스트 댓글"),
        () -> assertThat(savedComment.getCreatedAt()).isNotNull()
    );
  }
}
