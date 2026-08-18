package com.codeit.sb13.monew.comment.controller;


import com.codeit.sb13.monew.comment.service.CommentService;
import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.service.dto.CommentRegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

public class CommentController {

  public ResponseEntity<CommentDto> createComment(@PathVariable String articleId, @Valid @RequestBody CommentRegisterRequest request) {
    // 요청 처리 로직
    return ResponseEntity.status(HttpStatus.CREATED);

  }
}
