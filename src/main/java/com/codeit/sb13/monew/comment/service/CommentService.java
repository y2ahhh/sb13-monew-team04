package com.codeit.sb13.monew.comment.service;

import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.service.dto.CommentRegisterCommand;
import com.codeit.sb13.monew.comment.service.dto.CommentSearchCommand;
import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;

public interface CommentService {

  CommentDto create(CommentRegisterCommand command);

  CursorPageResponseDto<CommentDto> search(CommentSearchCommand command);
}
