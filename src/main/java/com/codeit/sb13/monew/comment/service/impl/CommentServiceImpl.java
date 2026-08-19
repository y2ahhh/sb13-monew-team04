package com.codeit.sb13.monew.comment.service.impl;

import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.comment.service.CommentService;
import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.service.dto.CommentRegisterCommand;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Validated // 서비스 계층에서도 Bean validation 적용하기 위해 추가
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class CommentServiceImpl implements CommentService {

  private final CommentRepository commentRepository;
  private final UserRepository userRepository;

  @Transactional
  @Override
  public CommentDto create(@Valid CommentRegisterCommand command) { // 서비스 전용객체를 사용
    User user = userRepository.findById(command.userId()).orElseThrow();
    Comment comment=new Comment(command.articleId(), user, command.content());
    Comment saved = commentRepository.save(comment);
    return CommentDto.from(saved);
  }
}
