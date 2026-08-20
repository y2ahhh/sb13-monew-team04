package com.codeit.sb13.monew.article.service.naver;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NaverNewsProperties.class)
public class NaverNewsConfig {

    private static final String CLIENT_ID_HEADER = "X-Naver-Client-Id";
    private static final String CLIENT_SECRET_HEADER = "X-Naver-Client-Secret";

    @Bean
    public RestClient naverNewsRestClient(NaverNewsProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(CLIENT_ID_HEADER, properties.clientId())
                .defaultHeader(CLIENT_SECRET_HEADER, properties.clientSecret())
                .build();
    }
}
