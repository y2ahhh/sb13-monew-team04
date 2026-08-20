package com.codeit.sb13.monew.article.service.naver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NaverNewsConfig 단위 테스트")
class NaverNewsConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NaverNewsConfig.class)
            .withPropertyValues(
                    "monew.news.naver.base-url=https://openapi.naver.com",
                    "monew.news.naver.path=/v1/search/news.json",
                    "monew.news.naver.client-id=client-id",
                    "monew.news.naver.client-secret=client-secret"
            );

    @Test
    @DisplayName("NAVER 뉴스 설정 값을 바인딩한다")
    void bindsNaverNewsProperties() {
        contextRunner.run(context -> {
            // when
            NaverNewsProperties properties = context.getBean(NaverNewsProperties.class);

            // then
            assertThat(properties.baseUrl()).isEqualTo("https://openapi.naver.com");
            assertThat(properties.path()).isEqualTo("/v1/search/news.json");
            assertThat(properties.clientId()).isEqualTo("client-id");
            assertThat(properties.clientSecret()).isEqualTo("client-secret");
        });
    }

    @Test
    @DisplayName("NAVER 뉴스 RestClient Bean을 생성한다")
    void createsNaverNewsRestClient() {
        contextRunner.run(context -> {
            assertThat(context.containsBean("naverNewsRestClient")).isTrue();
            assertThat(context.getBean("naverNewsRestClient", RestClient.class)).isNotNull();
        });
    }
}
