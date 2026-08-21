package com.codeit.sb13.monew.article.service.rss.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RSS 뉴스 설정 단위 테스트")
class RssNewsConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RssNewsConfig.class)
            .withPropertyValues(
                    "monew.news.rss.connect-timeout=2s",
                    "monew.news.rss.read-timeout=4s",
                    "monew.news.rss.hankyung.base-url=https://www.hankyung.com/feed",
                    "monew.news.rss.chosun.base-url=https://www.chosun.com/arc/outboundfeeds/rss/",
                    "monew.news.rss.yonhap.base-url=https://www.yonhapnewstv.co.kr"
            );

    @Test
    @DisplayName("RSS 뉴스 설정 값을 바인딩한다")
    void bindsRssNewsProperties() {
        contextRunner.run(context -> {
            RssNewsProperties properties = context.getBean(RssNewsProperties.class);

            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(4));
            assertThat(properties.hankyung().baseUrl()).isEqualTo("https://www.hankyung.com/feed");
            assertThat(properties.chosun().baseUrl()).isEqualTo("https://www.chosun.com/arc/outboundfeeds/rss/");
            assertThat(properties.yonhap().baseUrl()).isEqualTo("https://www.yonhapnewstv.co.kr");
        });
    }

    @Test
    @DisplayName("RSS 뉴스 RestClient Bean을 생성한다")
    void createsRssNewsRestClient() {
        contextRunner.run(context -> {
            assertThat(context.containsBean("rssNewsRestClient")).isTrue();
            assertThat(context.getBean("rssNewsRestClient", RestClient.class)).isNotNull();
        });
    }
}
