package com.codeit.sb13.monew.article.service.naver;

import com.codeit.sb13.monew.article.service.dto.CollectedArticle;

import java.util.List;

public interface NaverNewsClient {
    List<CollectedArticle> search(NaverNewsSearchRequest request);
}
