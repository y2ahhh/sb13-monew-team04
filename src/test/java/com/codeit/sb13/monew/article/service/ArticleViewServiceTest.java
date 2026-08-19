package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.article.service.impl.ArticleViewServiceImpl;
import com.codeit.sb13.monew.global.exception.article.ArticleNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleViewService 단위 테스트")
class ArticleViewServiceTest {

    @Mock
    private ArticleViewRepository articleViewRepository;

    @Mock
    private ArticleRepository articleRepository;

    @InjectMocks
    private ArticleViewServiceImpl articleViewService;

    private UUID testArticleId;
    private UUID testUserId;
    private Article testArticle;
    private ArticleView testArticleView;

    @BeforeEach
    void setUp() {
        testArticleId = UUID.randomUUID();
        testUserId = UUID.randomUUID();

        testArticle = new Article(
                "Test Article",
                "Test Summary",
                "https://example.com/article",
                LocalDateTime.now(),
                "NAVER"
        );

        testArticleView = new ArticleView(
                testArticleId,
                testUserId,
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("기사 조회 기록 생성 - 새로운 기록")
    void testRecordViewNewRecord() {
        // given
        when(articleRepository.findByIdAndDeletedAtIsNull(testArticleId))
                .thenReturn(Optional.of(testArticle));
        when(articleViewRepository.findByArticleIdAndUserId(testArticleId, testUserId))
                .thenReturn(Optional.empty());
        when(articleViewRepository.save(any(ArticleView.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        articleViewService.recordView(testArticleId, testUserId);

        // then
        verify(articleRepository, times(1)).findByIdAndDeletedAtIsNull(testArticleId);
        verify(articleViewRepository, times(1)).findByArticleIdAndUserId(testArticleId, testUserId);
        verify(articleViewRepository, times(1)).save(any(ArticleView.class));
    }

    @Test
    @DisplayName("기사 조회 기록 생성 - 기존 기록 업데이트")
    void testRecordViewExistingRecord() {
        // given
        when(articleRepository.findByIdAndDeletedAtIsNull(testArticleId))
                .thenReturn(Optional.of(testArticle));
        when(articleViewRepository.findByArticleIdAndUserId(testArticleId, testUserId))
                .thenReturn(Optional.of(testArticleView));
        when(articleViewRepository.save(any(ArticleView.class)))
                .thenReturn(testArticleView);

        // when
        articleViewService.recordView(testArticleId, testUserId);

        // then
        verify(articleRepository, times(1)).findByIdAndDeletedAtIsNull(testArticleId);
        verify(articleViewRepository, times(1)).findByArticleIdAndUserId(testArticleId, testUserId);
        verify(articleViewRepository, times(1)).save(any(ArticleView.class));
    }

    @Test
    @DisplayName("기사 조회 기록 생성 실패 - 기사 없음")
    void testRecordViewArticleNotFound() {
        // given
        when(articleRepository.findByIdAndDeletedAtIsNull(testArticleId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> articleViewService.recordView(testArticleId, testUserId))
                .isInstanceOf(ArticleNotFoundException.class);
        verify(articleRepository, times(1)).findByIdAndDeletedAtIsNull(testArticleId);
        verify(articleViewRepository, never()).save(any(ArticleView.class));
    }

    @Test
    @DisplayName("특정 기사의 조회수 조회")
    void testGetViewCount() {
        // given
        long expectedCount = 10L;
        when(articleViewRepository.countByArticleId(testArticleId))
                .thenReturn(expectedCount);

        // when
        long count = articleViewService.getViewCount(testArticleId);

        // then
        assertThat(count).isEqualTo(expectedCount);
        verify(articleViewRepository, times(1)).countByArticleId(testArticleId);
    }

    @Test
    @DisplayName("특정 사용자의 조회 기록 조회")
    void testGetUserArticleViews() {
        // given
        List<ArticleView> views = Arrays.asList(
                new ArticleView(UUID.randomUUID(), testUserId, LocalDateTime.now()),
                new ArticleView(UUID.randomUUID(), testUserId, LocalDateTime.now())
        );
        when(articleViewRepository.findByUserIdOrderByViewedAtDesc(testUserId))
                .thenReturn(views);

        // when
        List<ArticleView> result = articleViewService.getUserArticleViews(testUserId);

        // then
        assertThat(result).hasSize(2);
        verify(articleViewRepository, times(1)).findByUserIdOrderByViewedAtDesc(testUserId);
    }

    @Test
    @DisplayName("특정 기사의 조회 기록 조회")
    void testGetArticleViews() {
        // given
        List<ArticleView> views = Arrays.asList(
                new ArticleView(testArticleId, UUID.randomUUID(), LocalDateTime.now()),
                new ArticleView(testArticleId, UUID.randomUUID(), LocalDateTime.now())
        );
        when(articleViewRepository.findByArticleIdOrderByViewedAtDesc(testArticleId))
                .thenReturn(views);

        // when
        List<ArticleView> result = articleViewService.getArticleViews(testArticleId);

        // then
        assertThat(result).hasSize(2);
        verify(articleViewRepository, times(1)).findByArticleIdOrderByViewedAtDesc(testArticleId);
    }

    @Test
    @DisplayName("조회 기록이 없을 때 조회수 0 반환")
    void testGetViewCountZero() {
        // given
        when(articleViewRepository.countByArticleId(testArticleId))
                .thenReturn(0L);

        // when
        long count = articleViewService.getViewCount(testArticleId);

        // then
        assertThat(count).isZero();
        verify(articleViewRepository, times(1)).countByArticleId(testArticleId);
    }

    @Test
    @DisplayName("사용자의 조회 기록이 없을 때 빈 리스트 반환")
    void testGetUserArticleViewsEmpty() {
        // given
        when(articleViewRepository.findByUserIdOrderByViewedAtDesc(testUserId))
                .thenReturn(Arrays.asList());

        // when
        List<ArticleView> result = articleViewService.getUserArticleViews(testUserId);

        // then
        assertThat(result).isEmpty();
        verify(articleViewRepository, times(1)).findByUserIdOrderByViewedAtDesc(testUserId);
    }

    @Test
    @DisplayName("기사의 조회 기록이 없을 때 빈 리스트 반환")
    void testGetArticleViewsEmpty() {
        // given
        when(articleViewRepository.findByArticleIdOrderByViewedAtDesc(testArticleId))
                .thenReturn(Arrays.asList());

        // when
        List<ArticleView> result = articleViewService.getArticleViews(testArticleId);

        // then
        assertThat(result).isEmpty();
        verify(articleViewRepository, times(1)).findByArticleIdOrderByViewedAtDesc(testArticleId);
    }
}