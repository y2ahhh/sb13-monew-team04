package com.codeit.sb13.monew.comment.service;

import com.codeit.sb13.monew.comment.dto.CommentDto;
import com.codeit.sb13.monew.comment.dto.CommentRegisterRequest;

public interface CommentService {

  CommentDto create(CommentRegisterRequest request);
}
