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
    @DisplayName("검색형 어댑터는 요청 조건을 적용하고 출처가 일치하는 기사 후보를 반환한다")
    void searchAdapterAppliesRequestAndReturnsMatchingSourceArticles() {
        // given
        FakeSearchNewsSourceAdapter adapter = new FakeSearchNewsSourceAdapter();
        NewsFetchRequest request = new NewsFetchRequest("economy", 2);

        // when
        List<CollectedArticle> articles = adapter.fetch(request);

        // then
        assertThat(adapter.source()).isEqualTo(ArticleSource.NAVER);
        assertThat(adapter.lastRequest()).isEqualTo(request);
        assertThat(articles)
                .hasSize(request.limit())
                .allSatisfy(article -> {
                    assertThat(article.source()).isEqualTo(adapter.source());
                    assertThat(article.title()).contains(request.keyword());
                })
                .first()
                .satisfies(article -> {
                    assertThat(article.title()).isEqualTo("economy title 1");
                    assertThat(article.summary()).isEqualTo("summary");
                    assertThat(article.link()).isEqualTo("https://example.com/news/1");
                    assertThat(article.publishedAt()).isEqualTo(LocalDateTime.of(2026, 8, 20, 10, 0));
                });
    }

    private static class FakeSearchNewsSourceAdapter implements NewsSourceAdapter {
        private NewsFetchRequest lastRequest;

        @Override
        public ArticleSource source() {
            return ArticleSource.NAVER;
        }

        @Override
        public List<CollectedArticle> fetch(NewsFetchRequest request) {
            lastRequest = request;
            return List.of(
                            article("economy title 1", "https://example.com/news/1"),
                            article("economy title 2", "https://example.com/news/2"),
                            article("sports title 1", "https://example.com/news/3")
                    ).stream()
                    .filter(article -> article.title().contains(request.keyword()))
                    .limit(request.limit())
                    .toList();
        }

        private NewsFetchRequest lastRequest() {
            return lastRequest;
        }

        private CollectedArticle article(String title, String link) {
            return new CollectedArticle(
                    source(),
                    title,
                    "summary",
                    link,
                    LocalDateTime.of(2026, 8, 20, 10, 0)
            );
        }
    }
}
