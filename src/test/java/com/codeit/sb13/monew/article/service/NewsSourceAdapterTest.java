package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.dto.NewsFetchRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NewsSourceAdapter 계약 테스트")
class NewsSourceAdapterTest {

    @Test
    @DisplayName("어댑터는 출처와 수집 기사 후보 목록을 반환한다")
    void returnsSourceAndCollectedArticles() {
        // given
        NewsSourceAdapter adapter = new FakeNewsSourceAdapter();
        NewsFetchRequest request = new NewsFetchRequest("economy", 10);

        // when
        List<CollectedArticle> articles = adapter.fetch(request);

        // then
        assertThat(adapter.source()).isEqualTo(ArticleSource.NAVER);
        assertThat(articles)
                .hasSize(1)
                .first()
                .satisfies(article -> {
                    assertThat(article.source()).isEqualTo(ArticleSource.NAVER);
                    assertThat(article.title()).isEqualTo("title");
                    assertThat(article.summary()).isEqualTo("summary");
                    assertThat(article.link()).isEqualTo("https://example.com/news");
                    assertThat(article.publishedAt()).isEqualTo(LocalDateTime.of(2026, 8, 20, 10, 0));
                });
    }

    private static class FakeNewsSourceAdapter implements NewsSourceAdapter {

        @Override
        public ArticleSource source() {
            return ArticleSource.NAVER;
        }

        @Override
        public List<CollectedArticle> fetch(NewsFetchRequest request) {
            return List.of(new CollectedArticle(
                    source(),
                    "title",
                    "summary",
                    "https://example.com/news",
                    LocalDateTime.of(2026, 8, 20, 10, 0)
            ));
        }
    }
}
