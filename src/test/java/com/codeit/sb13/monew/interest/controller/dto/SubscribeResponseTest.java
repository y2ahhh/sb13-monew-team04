package com.codeit.sb13.monew.interest.controller.dto;

import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Subscribe;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubscribeResponseTest {

    @Nested
    @DisplayName("of()")
    class Of {

        @Test
        @DisplayName("Subscribe 엔티티와 구독자 수를 응답 DTO로 변환한다.")
        void of_toResponse() {
            // given
            Interest interest = Interest.create("스포츠");
            interest.addKeyword("축구");
            interest.addKeyword("야구");
            Subscribe subscribe = Subscribe.of(interest, UUID.randomUUID());

            // when
            SubscribeResponse response = SubscribeResponse.of(subscribe, 3L);

            // then
            assertThat(response.id()).isEqualTo(subscribe.getId());
            assertThat(response.interestId()).isEqualTo(interest.getId());
            assertThat(response.interestName()).isEqualTo(interest.getName());
            assertThat(response.interestKeywords()).containsExactly("축구", "야구");
            assertThat(response.interestSubscriberCount()).isEqualTo(3L);
            assertThat(response.createdAt()).isEqualTo(subscribe.getCreatedAt());
        }

        @Test
        @DisplayName("키워드가 없는 관심사를 구독하면 빈 목록으로 변환된다.")
        void of_withNoKeywords() {
            // given
            Interest interest = Interest.create("여행");
            Subscribe subscribe = Subscribe.of(interest, UUID.randomUUID());

            // when
            SubscribeResponse response = SubscribeResponse.of(subscribe, 0L);

            // then
            assertThat(response.interestKeywords()).isEmpty();
        }
    }
}
