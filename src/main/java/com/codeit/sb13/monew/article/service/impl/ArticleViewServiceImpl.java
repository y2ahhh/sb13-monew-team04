package com.codeit.sb13.monew.article.service.impl;

import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.article.service.ArticleViewService;
import com.codeit.sb13.monew.global.exception.article.ArticleNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleViewServiceImpl implements ArticleViewService {

    private final ArticleViewRepository articleViewRepository;
    private final ArticleRepository articleRepository;

    @Override
    @Transactional
    public void recordView(UUID articleId, UUID userId) {
        // 기사 확인
        articleRepository.findByIdAndDeletedAtIsNull(articleId)
                .orElseThrow(() -> new ArticleNotFoundException(articleId));

        // 기존 조회 기록 확인
        articleViewRepository.findByArticleIdAndUserId(articleId, userId)
                .ifPresentOrElse(
                        view -> articleViewRepository.save(view),  // 업데이트
                        () -> {
                            // ✅ 3개 필드 생성자 사용
                            ArticleView newView = new ArticleView(
                                    articleId,
                                    userId,
                                    null  // viewedAt는 @PrePersist에서 자동 설정
                            );
                            articleViewRepository.save(newView);
                        }
                );
    }

    @Override
    public long getViewCount(UUID articleId) {
        return articleViewRepository.countByArticleId(articleId);
    }

    @Override
    public List<ArticleView> getUserArticleViews(UUID userId) {
        return articleViewRepository.findByUserIdOrderByViewedAtDesc(userId);
    }

    @Override
    public List<ArticleView> getArticleViews(UUID articleId) {
        return articleViewRepository.findByArticleIdOrderByViewedAtDesc(articleId);
    }
}