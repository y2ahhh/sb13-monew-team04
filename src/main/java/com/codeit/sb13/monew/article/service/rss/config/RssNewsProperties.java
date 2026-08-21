package com.codeit.sb13.monew.article.service.rss.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "monew.news.rss")
public record RssNewsProperties(
        Duration connectTimeout,
        Duration readTimeout,
        Source hankyung,
        Source chosun,
        Source yonhap
) {

    public record Source(String baseUrl) {

    }
}
