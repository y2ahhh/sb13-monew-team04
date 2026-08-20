package com.codeit.sb13.monew.article.service.naver;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultNaverNewsSearchRequestProvider implements NaverNewsSearchRequestProvider {
    @Override
    public List<NaverNewsSearchRequest> getRequests() {
        return List.of();
    }
}
