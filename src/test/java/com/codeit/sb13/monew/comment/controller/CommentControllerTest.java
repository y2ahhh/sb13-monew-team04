package com.codeit.sb13.monew.comment.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.sb13.monew.comment.service.CommentService;
import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommentController.class)
@DisplayName("댓글 컨트롤러 - TDD")
public class CommentControllerTest {

  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  private CommentService commentService;

  @Test
  @DisplayName("댓글 생성 성공 - RED")
  void 댓글_생성_성공() throws Exception {
    // given
    UUID articleId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    CommentDto response=new CommentDto(UUID.randomUUID(), articleId, userId, "사용자 닉네임", "테스트 댓글", 0L, false, Instant.now());
    given(commentService.create(any())).willReturn(response);

    mockMvc.perform(
        post("/api/comments")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "articleId": "%s",
                  "userId": "%s",
                  "content": "테스트 댓글"
                }
                """.formatted(articleId, userId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(response.id().toString()))
        .andExpect(jsonPath("$.articleId").value(articleId.toString()))
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.content").value("테스트 댓글"))
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.nickname").value("사용자 닉네임"))
        .andExpect(jsonPath("$.likeCount").value(0))
        .andExpect(jsonPath("$.liked").value(false));


  }
}
