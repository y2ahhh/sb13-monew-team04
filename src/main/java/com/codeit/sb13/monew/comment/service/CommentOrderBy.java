package com.codeit.sb13.monew.comment.service;

import java.util.Arrays;

public enum CommentOrderBy {
  CREATED_AT("createdAt"),
  LIKE_COUNT("likeCount"),;

  private final String value;

  CommentOrderBy(String value) {
    this.value = value;
  }

  public static CommentOrderBy from(String value) {
    return Arrays.stream(values())
        .filter(orderBy -> orderBy.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 정렬 기준입니다: " + value));
  }
}
