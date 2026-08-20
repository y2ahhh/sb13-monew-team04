package com.codeit.sb13.monew.comment.repository;

import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.dto.RecentCommentActivityProjection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

  Optional<Comment> findByIdAndDeletedAtIsNull(UUID commentId);

  @Query("""
      SELECT new com.codeit.sb13.monew.comment.repository.dto.RecentCommentActivityProjection(
          c.id,
          a.id,
          a.title,
          u.id,
          u.nickname,
          c.content,
          0,
          c.createdAt
      )
      FROM Comment c
          JOIN c.user u
          JOIN c.article a
      WHERE
          u.id = :userId
          AND u.deletedAt IS NULL
          AND c.deletedAt IS NULL
          AND a.deletedAt IS NULL
      ORDER BY c.createdAt DESC
      LIMIT 10
      """)
  List<RecentCommentActivityProjection> findRecentCommentActivities(@Param("userId") UUID userId);
}
