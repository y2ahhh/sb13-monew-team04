package com.codeit.sb13.monew.article.repository;

import static com.codeit.sb13.monew.article.domain.QArticle.article;
import static com.codeit.sb13.monew.article.domain.QArticleView.articleView;
import static com.codeit.sb13.monew.comment.domain.QComment.comment;
import static com.codeit.sb13.monew.user.domain.QUser.user;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchCondition;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchRow;
import com.codeit.sb13.monew.article.service.dto.ArticleOrderBy;
import com.codeit.sb13.monew.user.domain.QUser;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ArticleRepositoryCustomImpl implements ArticleRepositoryCustom {

    private static final QUser commentUser = new QUser("commentUser");

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ArticleSearchRow> search(ArticleSearchCondition condition) {
        NumberExpression<Long> commentCountExpr = commentCountExpression();
        NumberExpression<Long> viewCountExpr = viewCountExpression();
        BooleanExpression viewedByMeExpr = viewedByMeExpression(condition.requestUserId());

        return queryFactory
                .select(Projections.constructor(
                        // 두 카운트 표현식은 서브쿼리를 감싼 것이라 getType()이 Long이 아니라 Object로 소실된다.
                        ArticleSearchRow.class, article,
                        commentCountExpr.longValue(), viewCountExpr.longValue(), viewedByMeExpr))
                .from(article)
                .where(
                        article.deletedAt.isNull(),
                        keywordContains(condition.keyword()),
                        sourceIn(condition.sourceIn()),
                        publishDateGoe(condition.publishDateFrom()),
                        publishDateLoe(condition.publishDateTo())
                )
                .orderBy(orderSpecifiers(
                        condition.orderBy(), condition.direction(),
                        commentCountExpr, viewCountExpr))
                .fetch();
    }

    private NumberExpression<Long> commentCountExpression() {
        return Expressions.asNumber(
                JPAExpressions.select(comment.count())
                        .from(comment)
                        .join(comment.user, commentUser)
                        .where(
                                comment.article.eq(article),
                                comment.deletedAt.isNull(),
                                commentUser.deletedAt.isNull()
                        )
        );
    }

    private NumberExpression<Long> viewCountExpression() {
        return Expressions.asNumber(
                JPAExpressions.select(articleView.count())
                        .from(articleView)
                        .join(articleView.user, user)
                        .where(articleView.article.eq(article), user.deletedAt.isNull())
        );
    }

    // 요청자가 이 기사를 조회한 적 있는지를 서브쿼리로 계산한다.
    private BooleanExpression viewedByMeExpression(UUID requestUserId) {
        if (requestUserId == null) {
            return Expressions.asBoolean(false);
        }

        return JPAExpressions.selectOne()
                .from(articleView)
                .where(articleView.article.eq(article), articleView.user.id.eq(requestUserId))
                .exists();
    }

    /**
     * 정렬 기준 값, 생성 시각, id 순으로 3단 정렬한다.
     *
     * <p>정렬 기준 값만으로는 동점인 기사들의 순서가 확정되지 않는다. 순서가 매 조회마다
     * 달라지면 커서 페이지네이션에서 항목이 중복되거나 누락되므로, 항상 유일한
     * {@code id}까지 내려가 순서를 확정한다. 세 기준을 모두 같은 방향으로 정렬해야
     * 페이지가 진행될수록 한쪽으로만 나아가는 유일한 순서가 유지된다.
     *
     * <p>MID4-109 관심사 목록 조회({@code InterestRepositoryCustomImpl})와 같은 구조다.
     * API마다 페이지네이션 동작이 달라지지 않도록 맞췄다.
     */
    private OrderSpecifier<?>[] orderSpecifiers(
            ArticleOrderBy orderBy,
            Sort.Direction direction,
            NumberExpression<Long> commentCountExpr,
            NumberExpression<Long> viewCountExpr
    ) {
        boolean ascending = direction.isAscending();

        OrderSpecifier<?> primary =
                primaryOrderSpecifier(orderBy, ascending, commentCountExpr, viewCountExpr);
        OrderSpecifier<?> tiebreaker = ascending ? article.createdAt.asc() : article.createdAt.desc();
        OrderSpecifier<?> idTiebreaker = ascending ? article.id.asc() : article.id.desc();

        return new OrderSpecifier<?>[] {primary, tiebreaker, idTiebreaker};
    }

    private OrderSpecifier<?> primaryOrderSpecifier(
            ArticleOrderBy orderBy,
            boolean ascending,
            NumberExpression<Long> commentCountExpr,
            NumberExpression<Long> viewCountExpr
    ) {
        if (orderBy == ArticleOrderBy.COMMENT_COUNT) {
            return ascending ? commentCountExpr.asc() : commentCountExpr.desc();
        }
        if (orderBy == ArticleOrderBy.VIEW_COUNT) {
            return ascending ? viewCountExpr.asc() : viewCountExpr.desc();
        }

        return ascending ? article.date.asc() : article.date.desc();
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return article.title.containsIgnoreCase(keyword)
                .or(article.summary.containsIgnoreCase(keyword));
    }

    private BooleanExpression sourceIn(List<ArticleSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        return article.source.in(sources);
    }

    private BooleanExpression publishDateGoe(LocalDateTime from) {
        return from == null ? null : article.date.goe(from);
    }

    private BooleanExpression publishDateLoe(LocalDateTime to) {
        return to == null ? null : article.date.loe(to);
    }
}