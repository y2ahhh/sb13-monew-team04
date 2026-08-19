package com.codeit.sb13.monew.comment.domain;

import com.codeit.sb13.monew.global.domain.DeletedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "comments")
public class Comment extends DeletedAtEntity { // DeletedAtEntity를 상속받아 공통 필드 사용하도록 수정

  @Column(name = "article_id", nullable = false)
  private UUID articleId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 500)
  private String content;

  public Comment(UUID articleId, UUID userId, String content) {
    this.articleId = articleId;
    this.userId = userId;
    this.content = content;
  }
}
