package com.codeit.sb13.monew.article.service.rss.client;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.rss.category.RssNewsCategory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RestClientRssNewsClient implements RssNewsClient {

    @Override
    public List<CollectedArticle> fetch(ArticleSource source, RssNewsCategory category) {
        return List.of();
    }
}
