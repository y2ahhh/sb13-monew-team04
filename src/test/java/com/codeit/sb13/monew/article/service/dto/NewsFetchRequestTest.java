package com.codeit.sb13.monew.article.service.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NewsFetchRequest 단위 테스트")
class NewsFetchRequestTest {

    @Test
    @DisplayName("뉴스 수집 요청 조건을 보존한다")
    void preservesFetchConditions() {
        // given
        String keyword = "economy";
        int limit = 10;

        // when
        NewsFetchRequest request = new NewsFetchRequest(keyword, limit);

        // then
        assertThat(request.keyword()).isEqualTo(keyword);
        assertThat(request.limit()).isEqualTo(limit);
    }
}
