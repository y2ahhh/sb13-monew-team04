package com.codeit.sb13.monew.comment.dto;

import com.codeit.sb13.monew.comment.entity.Comment;
import java.time.Instant;
import java.util.UUID;

public record CommentDto(
    UUID id,
    UUID articleId,
    UUID userId,
    String userNickname,
    String content,
    Long likeCount,
    boolean likedByMe,
    Instant createdAt
) {
  public static CommentDto from(Comment comment) {
    return new CommentDto(
        comment.getId(),
        comment.getArticleId(),
        comment.getUserId(),
        comment.getUserId().toString(), // Todo: 추후 사용자 도메인과 연동 후 실제 사용자 이름 조회
        comment.getContent(),
        0L, // Todo: 좋아요 기능 구현 후 수정
        false, // Todo: 좋아요 기능 구현 후 수정
        comment.getCreatedAt()
    );
  }
}
