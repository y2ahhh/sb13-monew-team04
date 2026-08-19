package com.codeit.sb13.monew.article.service.impl;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.article.service.dto.ArticleRequest;
import com.codeit.sb13.monew.global.exception.article.ArticleNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleServiceImpl implements ArticleService {

    private final ArticleRepository articleRepository;

    @Override
    public List<Article> findAll() {
        return articleRepository.findAllByDeletedAtIsNullOrderByDateDesc();
    }

    @Override
    public Article findById(UUID id) {
        return articleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
    }

    @Override
    @Transactional
    public Article save(ArticleRequest request) {
        // link 중복 체크
        if (articleRepository.findByLink(request.getLink()).isPresent()) {
            throw new IllegalArgumentException("이미 등록된 기사입니다.");
        }

        // ✅ 5개 필드 생성자 사용
        Article article = new Article(
                request.getTitle(),
                request.getSummary(),
                request.getLink(),
                request.getDate(),
                request.getSource()
        );

        return articleRepository.save(article);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Article article = findById(id);
        article.softDelete();
        articleRepository.save(article);
    }
}