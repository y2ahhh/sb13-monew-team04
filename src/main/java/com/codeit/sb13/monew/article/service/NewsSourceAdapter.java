package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.dto.NewsFetchRequest;

import java.util.List;

public interface NewsSourceAdapter {
    ArticleSource source();
    List<CollectedArticle> fetch(NewsFetchRequest request);
}
