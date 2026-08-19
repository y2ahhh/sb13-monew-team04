package com.codeit.sb13.monew.comment.controller;


import com.codeit.sb13.monew.comment.service.CommentService;
import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.controller.dto.CommentRegisterRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated // 클래스 수준 메서드 검증 활성화
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/comments")
@Tag(name = "Comment", description = "Comment API")
public class CommentController {

  private final CommentService commentService;

  @PostMapping
  public ResponseEntity<CommentDto> createComment(@Valid @RequestBody CommentRegisterRequest request) {
    // 요청 처리 로직
    return ResponseEntity.status(HttpStatus.CREATED).body(commentService.create(request.toCommand()));

  }
}
