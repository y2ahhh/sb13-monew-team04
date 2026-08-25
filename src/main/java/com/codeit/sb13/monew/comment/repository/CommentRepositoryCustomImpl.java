package com.codeit.sb13.monew.comment.repository;

import com.codeit.sb13.monew.comment.repository.dto.CommentSearchCondition;
import com.codeit.sb13.monew.comment.repository.dto.CommentSearchProjection;
import com.codeit.sb13.monew.comment.repository.dto.CommentSearchResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;

public class CommentRepositoryCustomImpl implements CommentRepositoryCustom {

  @PersistenceContext
  private EntityManager entityManager;

  @Override
  public CommentSearchResult search(CommentSearchCondition condition) {

    String direction = condition.direction() == Sort.Direction.ASC ? "asc" : "desc";

    StringBuilder jpql = new StringBuilder("""
        SELECT new com.codeit.sb13.monew.comment.repository.dto.CommentSearchProjection(
        C.id,
        A.id,
        U.id,
        U.nickname,
        C.content,
        0L,
        false,
        C.createdAt
        )
        FROM Comment C
        JOIN C.user U
        JOIN C.article A
        WHERE
            C.article.id = :articleId
            AND C.deletedAt IS NULL
            AND U.deletedAt IS NULL
            AND A.deletedAt IS NULL
        """);

    if (condition.after() != null) {
      jpql.append(condition.direction() == Sort.Direction.ASC?
          " AND C.createdAt > :after "
          : " AND C.createdAt < :after ");
    }


    jpql.append(" ORDER BY C.createdAt ")
        .append(direction)
        .append(", C.id ")
        .append(direction);

    TypedQuery<CommentSearchProjection> query = entityManager.createQuery(jpql.toString(), CommentSearchProjection.class);
    query.setParameter("articleId", condition.articleId());

    if (condition.after() != null) {
      query.setParameter("after", condition.after());
    }

    query.setMaxResults(condition.limit() + 1);

    List<CommentSearchProjection> rows = query.getResultList();
    boolean hasNext = rows.size() > condition.limit();

    if (hasNext) {
      rows = rows.subList(0, condition.limit());
    }

    long totalElements = countTotalElements(condition.articleId());

    return new CommentSearchResult(rows, hasNext, totalElements);
  }

  private long countTotalElements(UUID articleId) {
    TypedQuery<Long> query = entityManager.createQuery("""
        SELECT COUNT(C)
        FROM Comment C
            JOIN C.user U
            JOIN C.article A
        WHERE
            C.article.id = :articleId
            AND C.deletedAt IS NULL
            AND U.deletedAt IS NULL
            AND A.deletedAt IS NULL
        """, Long.class);

    query.setParameter("articleId", articleId);

    return query.getSingleResult();
  }
    
  }

