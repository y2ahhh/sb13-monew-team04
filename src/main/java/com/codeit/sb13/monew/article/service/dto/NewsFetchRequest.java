package com.codeit.sb13.monew.article.service.dto;

/**
 * 검색 기반 어댑터는 keyword를 사용하고, 피드 기반 어댑터는 무시할 수 있다.
 */
public record NewsFetchRequest(
        String keyword,
        int limit
) {
}
