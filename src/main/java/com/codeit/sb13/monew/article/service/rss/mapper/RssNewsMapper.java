package com.codeit.sb13.monew.article.service.rss.mapper;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RssNewsMapper {
    public List<CollectedArticle> toCollectedArticles(ArticleSource source, String xmlBody) {
        return List.of();
    }
}
