package com.codeit.sb13.monew.article.controller.dto;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.ArticleOrderBy;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ArticleSearchRequest(
        @Schema(description = "검색어(제목, 요약)", example = "반도체")
        String keyword,

        @Schema(description = "관심사 ID")
        UUID interestId,

        @Schema(description = "출처(포함)")
        List<ArticleSource> sourceIn,

        @Schema(description = "날짜 시작(범위)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishDateFrom,

        @Schema(description = "날짜 끝(범위)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishDateTo,

        // 바인딩은 ArticleOrderBy.from(String)이 담당한다.
        @Schema(description = "정렬 속성 이름", type = "string",
                allowableValues = {"publishDate", "commentCount", "viewCount"})
        ArticleOrderBy orderBy,

        @Schema(description = "정렬 방향", type = "string", allowableValues = {"ASC", "DESC"})
        Sort.Direction direction,

        @Schema(description = "이전 페이지 마지막 기사 ID")
        UUID cursor,

        @Schema(description = "보조 커서(이전 페이지 마지막 요소의 생성 시각)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime after,

        @Schema(description = "커서 페이지 크기", example = "50")
        int limit
) {
}