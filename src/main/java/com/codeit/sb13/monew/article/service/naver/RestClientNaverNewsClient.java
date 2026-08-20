package com.codeit.sb13.monew.article.service.naver;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchFailedException;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;


@Component
@RequiredArgsConstructor
public class RestClientNaverNewsClient implements NaverNewsClient {
    private static final String SOURCE = ArticleSource.NAVER.name();

    private final RestClient naverNewsRestClient;
    private final NaverNewsProperties props;
    private final NaverNewsMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<CollectedArticle> search(NaverNewsSearchRequest request) {
        try {
            String body = naverNewsRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(props.path())
                            .queryParam("query", request.query())
                            .queryParam("display", request.display())
                            .queryParam("start", request.start())
                            .queryParam("sort", request.sort().getValue())
                            .build()
                    )
                    .retrieve()
                    .body(String.class);

            NaverNewsSearchResponse response = objectMapper.readValue(body, NaverNewsSearchResponse.class);

            return mapper.toCollectedArticles(response);
        } catch (ArticleFetchParseException e) {
            throw e;
        } catch (JacksonException e) {
            throw new ArticleFetchParseException(SOURCE, e);
        } catch (RestClientException e) {
            throw new ArticleFetchFailedException(SOURCE, e);
        }
    }

}
