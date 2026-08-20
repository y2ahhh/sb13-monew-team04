package com.codeit.sb13.monew.interest.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.global.config.JpaAuditingConfig;
import com.codeit.sb13.monew.global.config.QueryDslConfig;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Subscribe;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchPage;
import com.codeit.sb13.monew.interest.service.dto.InterestOrderBy;
import com.codeit.sb13.monew.user.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@link InterestRepositoryCustomImpl#search}에 대한 통합 테스트.
 *
 * <p>{@code @DataJpaTest}는 {@link QueryDslConfig}나 {@link JpaAuditingConfig}처럼
 * 리포지토리/엔티티가 아닌 일반 {@code @Configuration}은 자동으로 스캔하지 않는다.
 * {@code JPAQueryFactory}를 쓰는 커스텀 구현을 테스트하려면 {@link QueryDslConfig}가,
 * {@code @CreatedDate}로 채워지는 {@code createdAt}(커서의 보조 기준으로 쓰인다)이
 * 실제로 채워지려면 {@link JpaAuditingConfig}가 각각 필요해 둘 다 명시적으로
 * {@code @Import}한다.</p>
 */
@DataJpaTest
@Import({QueryDslConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class InterestRepositoryCustomImplTest {

    @Autowired
    private InterestRepository interestRepository;

    @Autowired
    private TestEntityManager em;

    private UUID persistUser() {
        User user = User.builder()
                .email(UUID.randomUUID() + "@test.com")
                .nickname("테스터")
                .password("password")
                .build();
        em.persist(user);
        return user.getId();
    }

    private Interest persistInterest(String name, String... keywords) {
        Interest interest = Interest.create(name);
        for (String keyword : keywords) {
            interest.addKeyword(keyword);
        }
        em.persist(interest);
        return interest;
    }

    private void subscribe(Interest interest, UUID userId) {
        em.persist(Subscribe.of(interest, userId));
    }

    @Test
    @DisplayName("정렬 기준이 이름이면 오름차순으로 정렬해 반환한다")
    void search_orderByNameAscending_returnsSortedByName() {
        persistInterest("다람쥐", "동물");
        persistInterest("가나다", "동물");
        persistInterest("나비", "동물");
        em.flush();
        em.clear();

        InterestSearchPage page = interestRepository.search(
                null, InterestOrderBy.NAME, Sort.Direction.ASC, null, null, 10, null);

        assertThat(page.interests()).extracting(Interest::getName)
                .containsExactly("가나다", "나비", "다람쥐");
        assertThat(page.hasNext()).isFalse();
        assertThat(page.totalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("검색어는 관심사 이름과 키워드 텍스트 모두에서 매칭된다")
    void search_keywordFilter_matchesNameOrKeywordText() {
        persistInterest("스포츠", "축구");
        persistInterest("여행", "스포츠용품");
        persistInterest("요리", "레시피");
        em.flush();
        em.clear();

        InterestSearchPage page = interestRepository.search(
                "스포츠", InterestOrderBy.NAME, Sort.Direction.ASC, null, null, 10, null);

        assertThat(page.interests()).extracting(Interest::getName)
                .containsExactlyInAnyOrder("스포츠", "여행");
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("구독자 수와 요청자의 구독 여부를 정확히 계산한다")
    void search_computesSubscriberCountAndSubscribedByMe() {
        UUID requester = persistUser();
        UUID other = persistUser();

        Interest popular = persistInterest("인기관심사", "키워드");
        Interest lonely = persistInterest("비인기관심사", "키워드");
        em.flush();

        subscribe(popular, requester);
        subscribe(popular, other);
        em.flush();
        em.clear();

        InterestSearchPage page = interestRepository.search(
                null, InterestOrderBy.NAME, Sort.Direction.ASC, null, null, 10, requester);

        assertThat(page.subscriberCounts().get(popular.getId())).isEqualTo(2L);
        assertThat(page.subscriberCounts().getOrDefault(lonely.getId(), 0L)).isEqualTo(0L);
        assertThat(page.subscribedInterestIds()).contains(popular.getId());
        assertThat(page.subscribedInterestIds()).doesNotContain(lonely.getId());
    }

    @Test
    @DisplayName("구독자 수 기준 내림차순 정렬이 정확하다")
    void search_orderBySubscriberCountDescending_returnsSortedBySubscriberCount() {
        UUID userA = persistUser();
        UUID userB = persistUser();

        Interest twoSubscribers = persistInterest("A", "키워드");
        Interest oneSubscriber = persistInterest("B", "키워드");
        persistInterest("C", "키워드");
        em.flush();

        subscribe(twoSubscribers, userA);
        subscribe(twoSubscribers, userB);
        subscribe(oneSubscriber, userA);
        em.flush();
        em.clear();

        InterestSearchPage page = interestRepository.search(
                null, InterestOrderBy.SUBSCRIBER_COUNT, Sort.Direction.DESC, null, null, 10, null);

        assertThat(page.interests()).extracting(Interest::getName)
                .containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("커서와 보조 커서를 넘기면 이전 페이지 다음부터 이어서 조회한다")
    void search_withCursorAndAfter_continuesFromPreviousPage() {
        persistInterest("가", "키워드");
        persistInterest("나", "키워드");
        persistInterest("다", "키워드");
        persistInterest("라", "키워드");
        persistInterest("마", "키워드");
        em.flush();
        em.clear();

        InterestSearchPage firstPage = interestRepository.search(
                null, InterestOrderBy.NAME, Sort.Direction.ASC, null, null, 2, null);

        assertThat(firstPage.interests()).extracting(Interest::getName)
                .containsExactly("가", "나");
        assertThat(firstPage.hasNext()).isTrue();

        Interest lastOfFirstPage = firstPage.interests().get(firstPage.interests().size() - 1);
        String cursor = lastOfFirstPage.getName();
        LocalDateTime after = lastOfFirstPage.getCreatedAt();

        InterestSearchPage secondPage = interestRepository.search(
                null, InterestOrderBy.NAME, Sort.Direction.ASC, cursor, after, 2, null);

        assertThat(secondPage.interests()).extracting(Interest::getName)
                .containsExactly("다", "라");
        assertThat(secondPage.hasNext()).isTrue();

        InterestSearchPage thirdPage = interestRepository.search(
                null, InterestOrderBy.NAME, Sort.Direction.ASC,
                secondPage.interests().get(1).getName(), secondPage.interests().get(1).getCreatedAt(),
                2, null);

        assertThat(thirdPage.interests()).extracting(Interest::getName)
                .containsExactly("마");
        assertThat(thirdPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("페이지 안에 담긴 각 관심사의 키워드도 함께 채워진다")
    void search_populatesKeywordsForEachInterest() {
        persistInterest("스포츠", "축구", "야구");
        em.flush();
        em.clear();

        InterestSearchPage page = interestRepository.search(
                null, InterestOrderBy.NAME, Sort.Direction.ASC, null, null, 10, null);

        assertThat(page.interests()).hasSize(1);
        assertThat(page.interests().get(0).getKeywords())
                .extracting(k -> k.getKeyword())
                .containsExactlyInAnyOrder("축구", "야구");
    }
}
