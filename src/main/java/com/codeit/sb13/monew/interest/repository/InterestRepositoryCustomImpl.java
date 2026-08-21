package com.codeit.sb13.monew.interest.repository;

import static com.codeit.sb13.monew.interest.domain.QInterest.interest;
import static com.codeit.sb13.monew.interest.domain.QSubscribe.subscribe;

import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.global.exception.interest.InterestSearchConditionInvalidException;
import com.codeit.sb13.monew.interest.domain.QKeyword;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchCondition;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchPage;
import com.codeit.sb13.monew.interest.service.dto.InterestOrderBy;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

/**
 * {@link InterestRepositoryCustom}의 QueryDSL 구현.
 *
 * <p>구독자 수는 별도 컬럼이 아니라 {@code subscriptions}에 대한 상관 서브쿼리로 계산한다.
 * 키워드 매칭도 {@code keywords} 테이블에 대한 EXISTS 서브쿼리로 처리하는데, 두 방식 모두
 * JOIN 대신 서브쿼리를 쓰는 이유는 같다. {@code interests}가 {@code keywords}(1:N)나
 * {@code subscriptions}(1:N)와 조인되면 관심사 하나가 여러 행으로 뻥튀기(fan-out)되어,
 * 페이지네이션의 LIMIT이 관심사 개수가 아니라 조인된 행 개수를 자르게 되고 구독자 수
 * 집계도 흐트러진다. 서브쿼리는 관심사 1건당 정확히 1행을 유지하면서 값만 끌어오므로
 * 이 문제가 생기지 않는다.</p>
 */
@RequiredArgsConstructor
public class InterestRepositoryCustomImpl implements InterestRepositoryCustom {

    /**
     * {@code Keyword} 엔티티의 속성 이름이 하필 {@code keyword}라서, QueryDSL이 관례로
     * 만들어주는 기본 인스턴스({@code QKeyword.keyword})는 같은 이름의 필드와 충돌해
     * 생성되지 않는다. 그래서 별칭을 직접 지정해 인스턴스를 만든다.
     */
    private static final QKeyword keyword = new QKeyword("keyword");

    private final JPAQueryFactory queryFactory;

    @Override
    public InterestSearchPage search(InterestSearchCondition condition) {
        String keywordText = condition.keyword();
        InterestOrderBy orderBy = condition.orderBy();
        Sort.Direction direction = condition.direction();
        String cursor = condition.cursor();
        LocalDateTime after = condition.after();
        int limit = condition.limit();
        UUID requestUserId = condition.requestUserId();

        NumberExpression<Long> subscriberCountExpr = subscriberCountExpression();
        BooleanExpression searchCondition = searchCondition(keywordText);
        BooleanExpression keysetCondition = keysetCondition(orderBy, direction, cursor, after, subscriberCountExpr);

        // 다음 페이지 존재 여부를 별도 쿼리 없이 판단하기 위해 요청한 limit보다 하나 더 가져온다.
        List<Tuple> rows = queryFactory
                .select(interest, subscriberCountExpr)
                .from(interest)
                .where(searchCondition, keysetCondition)
                .orderBy(orderSpecifiers(orderBy, direction, subscriberCountExpr))
                .limit(limit + 1L)
                .fetch();

        boolean hasNext = rows.size() > limit;
        List<Tuple> pageRows = hasNext ? rows.subList(0, limit) : rows;

        List<Interest> pageInterests = pageRows.stream()
                .map(row -> row.get(interest))
                .toList();

        Map<UUID, Long> subscriberCounts = pageRows.stream()
                .collect(Collectors.toMap(
                        row -> row.get(interest).getId(),
                        row -> row.get(subscriberCountExpr)
                ));

        fetchKeywordsInto(pageInterests);

        Set<UUID> subscribedInterestIds = requestUserId == null
                ? Set.of()
                : findSubscribedInterestIds(requestUserId, pageInterests);

        Long totalElements = queryFactory
                .select(interest.count())
                .from(interest)
                .where(searchCondition)
                .fetchOne();

        return new InterestSearchPage(
                pageInterests,
                subscriberCounts,
                subscribedInterestIds,
                hasNext,
                totalElements == null ? 0L : totalElements
        );
    }

    private NumberExpression<Long> subscriberCountExpression() {
        return Expressions.asNumber(
                JPAExpressions.select(subscribe.count())
                        .from(subscribe)
                        .where(subscribe.interest.eq(interest))
        );
    }

    private BooleanExpression searchCondition(String keywordText) {
        if (!StringUtils.hasText(keywordText)) {
            return null;
        }

        BooleanExpression nameMatches = interest.name.containsIgnoreCase(keywordText);
        BooleanExpression keywordMatches = JPAExpressions.selectOne()
                .from(keyword)
                .where(
                        keyword.interest.eq(interest),
                        keyword.keyword.containsIgnoreCase(keywordText)
                )
                .exists();

        return nameMatches.or(keywordMatches);
    }

    /**
     * 커서 기반(keyset) 페이지네이션 조건을 만든다.
     *
     * <p>정렬 기준 값이 커서 값보다 "다음" 쪽(방향에 따라 크거나 작은 쪽)이면 그대로 통과,
     * 정렬 기준 값이 커서와 완전히 같다면 생성 시각을 보조 기준으로 비교한다. 보조 기준을
     * 주 기준과 같은 방향으로 비교해야, 정렬 기준 값이 같은 항목들 사이에서도 페이지가
     * 진행될수록 항상 "다음" 쪽으로만 나아가는 일관된 순서가 유지된다.</p>
     */
    private BooleanExpression keysetCondition(
            InterestOrderBy orderBy,
            Sort.Direction direction,
            String cursor,
            LocalDateTime after,
            NumberExpression<Long> subscriberCountExpr
    ) {
        if (!StringUtils.hasText(cursor) || after == null) {
            return null;
        }

        boolean asc = direction.isAscending();

        if (orderBy == InterestOrderBy.NAME) {
            BooleanExpression primary = asc ? interest.name.gt(cursor) : interest.name.lt(cursor);
            BooleanExpression tie = interest.name.eq(cursor)
                    .and(asc ? interest.createdAt.gt(after) : interest.createdAt.lt(after));
            return primary.or(tie);
        }

        long cursorCount = parseCursorAsCount(cursor);
        BooleanExpression primary = asc ? subscriberCountExpr.gt(cursorCount) : subscriberCountExpr.lt(cursorCount);
        BooleanExpression tie = subscriberCountExpr.eq(cursorCount)
                .and(asc ? interest.createdAt.gt(after) : interest.createdAt.lt(after));
        return primary.or(tie);
    }

    private long parseCursorAsCount(String cursor) {
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new InterestSearchConditionInvalidException("구독자 수 기준 커서 값이 올바르지 않습니다: " + cursor);
        }
    }

    private OrderSpecifier<?>[] orderSpecifiers(
            InterestOrderBy orderBy,
            Sort.Direction direction,
            NumberExpression<Long> subscriberCountExpr
    ) {
        boolean ascending = direction.isAscending();

        OrderSpecifier<?> primary = orderBy == InterestOrderBy.NAME
                ? (ascending ? interest.name.asc() : interest.name.desc())
                : (ascending ? subscriberCountExpr.asc() : subscriberCountExpr.desc());

        OrderSpecifier<?> tiebreaker = ascending ? interest.createdAt.asc() : interest.createdAt.desc();

        return new OrderSpecifier<?>[] {primary, tiebreaker};
    }

    /**
     * 이미 확정된 관심사 목록에 키워드를 한 번에 fetch join으로 채워 넣는다.
     *
     * <p>같은 영속성 컨텍스트 안에서는 id가 같은 엔티티가 항상 같은 인스턴스로 관리되므로,
     * 이 메서드가 반환하는 리스트를 따로 쓰지 않아도 {@code interests}에 담긴 인스턴스들의
     * keywords 컬렉션이 그대로 초기화된다.</p>
     */
    private void fetchKeywordsInto(List<Interest> interests) {
        if (interests.isEmpty()) {
            return;
        }

        queryFactory
                .selectFrom(interest)
                .distinct()
                .leftJoin(interest.keywords, keyword).fetchJoin()
                .where(interest.in(interests))
                .fetch();
    }

    private Set<UUID> findSubscribedInterestIds(UUID requestUserId, List<Interest> interests) {
        if (interests.isEmpty()) {
            return Set.of();
        }

        return Set.copyOf(queryFactory
                .select(subscribe.interest.id)
                .from(subscribe)
                .where(subscribe.userId.eq(requestUserId), subscribe.interest.in(interests))
                .fetch());
    }
}
