package com.codeit.sb13.monew.comment.service;

import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.service.dto.CommentRegisterRequest;

public interface CommentService {

  CommentDto create(CommentRegisterRequest request);
}
