package com.codeit.sb13.monew.article.service.naver;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monew.news.naver")
public record NaverNewsProperties(
        String baseUrl,
        String path,
        String clientId,
        String clientSecret
) {
}
