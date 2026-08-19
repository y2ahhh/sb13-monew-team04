package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.service.dto.ArticleRequest;
import com.codeit.sb13.monew.article.service.impl.ArticleServiceImpl;
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
@DisplayName("ArticleService 단위 테스트")
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @InjectMocks
    private ArticleServiceImpl articleService;

    private Article testArticle;
    private UUID testArticleId;
    private ArticleRequest articleRequest;

    @BeforeEach
    void setUp() {
        testArticleId = UUID.randomUUID();
        testArticle = new Article(
                "Test Article",
                "Test Summary",
                "https://example.com/article",
                LocalDateTime.now(),
                "NAVER"
        );

        articleRequest = new ArticleRequest(
                "New Article",
                "New Summary",
                "https://example.com/new",
                LocalDateTime.now(),
                "HANKYUNG"
        );
    }

    @Test
    @DisplayName("모든 활성 기사 조회 (최신순)")
    void testFindAll() {
        // given
        List<Article> articles = Arrays.asList(
                new Article("title 1", "summary 1", "link1", LocalDateTime.now(), "source1"),
                new Article("title 2", "summary 2", "link2", LocalDateTime.now(), "source2")
        );
        when(articleRepository.findAllByDeletedAtIsNullOrderByDateDesc())
                .thenReturn(articles);

        // when
        List<Article> result = articleService.findAll();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("title 1");
        assertThat(result.get(1).getTitle()).isEqualTo("title 2");
        verify(articleRepository, times(1)).findAllByDeletedAtIsNullOrderByDateDesc();
    }

    @Test
    @DisplayName("ID로 기사 조회 성공")
    void testFindByIdSuccess() {
        // given
        when(articleRepository.findByIdAndDeletedAtIsNull(testArticleId))
                .thenReturn(Optional.of(testArticle));

        // when
        Article result = articleService.findById(testArticleId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Test Article");
        verify(articleRepository, times(1)).findByIdAndDeletedAtIsNull(testArticleId);
    }

    @Test
    @DisplayName("ID로 기사 조회 실패 - 기사 없음")
    void testFindByIdNotFound() {
        // given
        when(articleRepository.findByIdAndDeletedAtIsNull(testArticleId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> articleService.findById(testArticleId))
                .isInstanceOf(ArticleNotFoundException.class);
        verify(articleRepository, times(1)).findByIdAndDeletedAtIsNull(testArticleId);
    }

    @Test
    @DisplayName("기사 저장 성공")
    void testSaveSuccess() {
        // given
        when(articleRepository.findByLink(articleRequest.getLink()))
                .thenReturn(Optional.empty());

        Article savedArticle = new Article(
                articleRequest.getTitle(),
                articleRequest.getSummary(),
                articleRequest.getLink(),
                articleRequest.getDate(),
                articleRequest.getSource()
        );
        when(articleRepository.save(any(Article.class)))
                .thenReturn(savedArticle);

        // when
        Article result = articleService.save(articleRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo(articleRequest.getTitle());
        assertThat(result.getLink()).isEqualTo(articleRequest.getLink());
        verify(articleRepository, times(1)).findByLink(articleRequest.getLink());
        verify(articleRepository, times(1)).save(any(Article.class));
    }

    @Test
    @DisplayName("기사 저장 실패 - 중복된 링크")
    void testSaveDuplicateLink() {
        // given
        when(articleRepository.findByLink(articleRequest.getLink()))
                .thenReturn(Optional.of(testArticle));

        // when & then
        assertThatThrownBy(() -> articleService.save(articleRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 등록된 기사입니다.");
        verify(articleRepository, times(1)).findByLink(articleRequest.getLink());
        verify(articleRepository, never()).save(any(Article.class));
    }

    @Test
    @DisplayName("기사 삭제 (논리 삭제)")
    void testDeleteSuccess() {
        // given
        when(articleRepository.findByIdAndDeletedAtIsNull(testArticleId))
                .thenReturn(Optional.of(testArticle));
        when(articleRepository.save(any(Article.class)))
                .thenReturn(testArticle);

        // when
        articleService.delete(testArticleId);

        // then
        verify(articleRepository, times(1)).findByIdAndDeletedAtIsNull(testArticleId);
        verify(articleRepository, times(1)).save(any(Article.class));
    }

    @Test
    @DisplayName("기사 삭제 실패 - 기사 없음")
    void testDeleteNotFound() {
        // given
        when(articleRepository.findByIdAndDeletedAtIsNull(testArticleId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> articleService.delete(testArticleId))
                .isInstanceOf(ArticleNotFoundException.class);
        verify(articleRepository, times(1)).findByIdAndDeletedAtIsNull(testArticleId);
        verify(articleRepository, never()).save(any(Article.class));
    }
}