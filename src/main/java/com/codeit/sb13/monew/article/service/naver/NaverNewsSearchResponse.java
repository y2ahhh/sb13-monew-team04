package com.codeit.sb13.monew.article.service.naver;

import java.util.List;

public record NaverNewsSearchResponse(
        String lastBuildDate,
        Integer total,
        Integer start,
        Integer display,
        List<NaverNewsItem> items
) {
}
