package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.service.dto.ArticleRequest;

import java.util.List;
import java.util.UUID;

public interface ArticleService {

    //모든 활성 기사 조회 (최신순)
    List<Article> findAll();

    // ID로 기사 조회
    Article findById(UUID id);

    Article save(ArticleRequest request);

    void delete(UUID id);
}