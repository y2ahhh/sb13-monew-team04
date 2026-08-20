package com.codeit.sb13.monew.article.mapper;

import com.codeit.sb13.monew.article.controller.dto.ArticleDto;
import com.codeit.sb13.monew.article.controller.dto.ArticleViewDto;
import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ArticleMapper {

    @Mapping(source = "article.link", target = "sourceUrl")
    @Mapping(source = "article.date", target = "publishDate")
    ArticleDto toDto(Article article, boolean viewedByMe);

    @Mapping(source = "user.id", target = "viewedBy")
    @Mapping(source = "article.id", target = "articleId")
    @Mapping(source = "article.source", target = "source")
    @Mapping(source = "article.link", target = "sourceUrl")
    @Mapping(source = "article.title", target = "articleTitle")
    @Mapping(source = "article.date", target = "articlePublishedDate")
    @Mapping(source = "article.summary", target = "articleSummary")
    @Mapping(source = "article.commentCount", target = "articleCommentCount")
    @Mapping(source = "article.viewCount", target = "articleViewCount")
    ArticleViewDto toViewDto(ArticleView articleView);
}