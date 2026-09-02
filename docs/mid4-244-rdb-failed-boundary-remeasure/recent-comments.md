# 최근 작성 댓글 실행계획

> [MID4-244 요약](README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

이 문서는 최근 작성 댓글과 댓글별 활성 좋아요 수 조회에 같은 SQL을 사용하고, 기존 인덱스와 부분 커버링 인덱스를 비교한 결과다.

## 한눈에 보기

- 부분 커버링 인덱스 적용 후 일반 SELECT 중앙값은 데이터 몰림 조건에서 18.2%, 2배 조건에서 19.3% 개선됐다.
- 실제 테이블 방문은 줄었지만 좋아요 수를 세는 하위 조회는 그대로 남아 데이터가 늘면 집계 비용도 함께 증가한다.

## 실행 SQL

```sql
-- MID4-227 변경 반영 후: 최근 작성 댓글과 댓글별 활성 좋아요 수를 조회합니다.
-- CommentRepository.findRecentCommentActivities의 PostgreSQL 대응 SQL입니다.
SELECT
    c.id,
    a.id AS article_id,
    a.title AS article_title,
    u.id AS user_id,
    u.nickname AS user_nickname,
    c.content,
    (
        SELECT count(cl.id)
        FROM comment_likes cl
        WHERE cl.comment_id = c.id
          AND cl.visibility_status = 'ACTIVE'
    ) AS like_count,
    c.created_at
FROM comments c
JOIN users u ON u.id = c.user_id
JOIN articles a ON a.id = c.article_id
WHERE u.id = '00000001-0000-4000-8000-000000000001'
  AND c.visibility_status = 'ACTIVE'
ORDER BY c.created_at DESC, c.id DESC
FETCH FIRST 10 ROWS ONLY;
```

## 반복 측정

각 SQL을 3회 미리 실행한 뒤 일반 SELECT와 EXPLAIN을 각각 5회 측정했다. 아래 표의 실행 시간은 일반 SELECT 5회의 가운데 값이며 단위는 ms다. 개선율이 양수이면 후보 인덱스가 빨라진 것이다.

공유 버퍼는 아래 대표 실행계획의 `shared hit` 값이다. 워밍업 뒤 모든 대표 실행계획에서 디스크 읽기인 `shared read`는 `0`이었다.

| 데이터 조건 | 기존 인덱스 | 후보 인덱스 | 개선율 | 대표 실행계획 공유 버퍼(기존 → 후보) |
| --- | ---: | ---: | ---: | ---: |
| 데이터 몰림 조건 | 3.537 | 2.894 | 18.2% | 313 → 158 |
| 데이터 몰림 2배 조건 | 4.516 | 3.644 | 19.3% | 537 → 218 |

## 동일 조건 실행계획 원문

데이터 몰림 조건은 후보 인덱스를 사용한 실행계획을 기록했다. 데이터 몰림 2배 조건은 기존 인덱스와 후보 인덱스를 사용한 실행계획을 모두 기록했다. 실행계획의 `Execution Time`은 일반 SELECT 중앙값과 별도로 측정한 값이므로 서로 다를 수 있다.

### 데이터 몰림 조건 · 후보 인덱스 실행계획

```text
QUERY PLAN
---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1.27..152.97 rows=10 width=138) (actual time=0.162..0.969 rows=10 loops=1)
   Output: c.id, a.id, a.title, u.id, u.nickname, c.content, ((SubPlan 1)), c.created_at
   Buffers: shared hit=158
   ->  Nested Loop  (cost=1.27..139769.32 rows=9214 width=138) (actual time=0.161..0.967 rows=10 loops=1)
         Output: c.id, a.id, a.title, u.id, u.nickname, c.content, (SubPlan 1), c.created_at
         Inner Unique: true
         Buffers: shared hit=158
         ->  Nested Loop  (cost=0.85..37654.65 rows=9214 width=103) (actual time=0.038..0.045 rows=10 loops=1)
               Output: c.id, c.content, c.created_at, c.article_id, u.id, u.nickname
               Buffers: shared hit=8
               ->  Index Scan using idx_comments_user_created_id on public.comments c  (cost=0.43..37531.04 rows=9214 width=88) (actual time=0.020..0.023 rows=10 loops=1)
                     Output: c.id, c.content, c.created_at, c.user_id, c.article_id
                     Index Cond: (c.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: ((c.visibility_status)::text = 'ACTIVE'::text)
                     Buffers: shared hit=4
               ->  Materialize  (cost=0.42..8.44 rows=1 width=31) (actual time=0.002..0.002 rows=1 loops=10)
                     Output: u.id, u.nickname
                     Buffers: shared hit=4
                     ->  Index Scan using pk_users on public.users u  (cost=0.42..8.44 rows=1 width=31) (actual time=0.014..0.014 rows=1 loops=1)
                           Output: u.id, u.nickname
                           Index Cond: (u.id = '00000001-0000-4000-8000-000000000001'::uuid)
                           Buffers: shared hit=4
         ->  Index Scan using pk_articles on public.articles a  (cost=0.43..6.62 rows=1 width=43) (actual time=0.002..0.002 rows=1 loops=10)
               Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
               Index Cond: (a.id = c.article_id)
               Buffers: shared hit=40
         SubPlan 1
           ->  Aggregate  (cost=4.45..4.46 rows=1 width=8) (actual time=0.090..0.090 rows=1 loops=10)
                 Output: count(cl.id)
                 Buffers: shared hit=110
                 ->  Index Only Scan using idx_perf_comment_likes_active_comment_cover on public.comment_likes cl  (cost=0.43..4.45 rows=1 width=16) (actual time=0.005..0.059 rows=1001 loops=10)
                       Output: cl.comment_id, cl.id
                       Index Cond: (cl.comment_id = c.id)
                       Heap Fetches: 0
                       Buffers: shared hit=110
 Planning:
   Buffers: shared hit=404
 Planning Time: 1.097 ms
 Execution Time: 1.030 ms
(39 rows)
```

### 데이터 몰림 2배 조건 · 기존 인덱스 실행계획

```text
QUERY PLAN
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1.27..217.57 rows=10 width=138) (actual time=0.483..3.423 rows=10 loops=1)
   Output: c.id, a.id, a.title, u.id, u.nickname, c.content, ((SubPlan 1)), c.created_at
   Buffers: shared hit=537
   ->  Nested Loop  (cost=1.27..199511.88 rows=9224 width=138) (actual time=0.482..3.420 rows=10 loops=1)
         Output: c.id, a.id, a.title, u.id, u.nickname, c.content, (SubPlan 1), c.created_at
         Inner Unique: true
         Buffers: shared hit=537
         ->  Nested Loop  (cost=0.85..37766.28 rows=9224 width=103) (actual time=0.036..0.048 rows=10 loops=1)
               Output: c.id, c.content, c.created_at, c.article_id, u.id, u.nickname
               Buffers: shared hit=8
               ->  Index Scan using idx_comments_user_created_id on public.comments c  (cost=0.43..37642.55 rows=9224 width=88) (actual time=0.019..0.024 rows=10 loops=1)
                     Output: c.id, c.content, c.created_at, c.user_id, c.article_id
                     Index Cond: (c.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: ((c.visibility_status)::text = 'ACTIVE'::text)
                     Buffers: shared hit=4
               ->  Materialize  (cost=0.42..8.44 rows=1 width=31) (actual time=0.002..0.002 rows=1 loops=10)
                     Output: u.id, u.nickname
                     Buffers: shared hit=4
                     ->  Index Scan using pk_users on public.users u  (cost=0.42..8.44 rows=1 width=31) (actual time=0.013..0.013 rows=1 loops=1)
                           Output: u.id, u.nickname
                           Index Cond: (u.id = '00000001-0000-4000-8000-000000000001'::uuid)
                           Buffers: shared hit=4
         ->  Index Scan using pk_articles on public.articles a  (cost=0.43..6.62 rows=1 width=43) (actual time=0.002..0.002 rows=1 loops=10)
               Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
               Index Cond: (a.id = c.article_id)
               Buffers: shared hit=40
         SubPlan 1
           ->  Aggregate  (cost=10.90..10.91 rows=1 width=8) (actual time=0.333..0.334 rows=1 loops=10)
                 Output: count(cl.id)
                 Buffers: shared hit=489
                 ->  Index Scan using uk_comment_likes_comment_liked_by on public.comment_likes cl  (cost=0.43..10.90 rows=1 width=16) (actual time=0.005..0.268 rows=2001 loops=10)
                       Output: cl.id
                       Index Cond: (cl.comment_id = c.id)
                       Filter: ((cl.visibility_status)::text = 'ACTIVE'::text)
                       Buffers: shared hit=489
 Planning:
   Buffers: shared hit=374
 Planning Time: 0.830 ms
 Execution Time: 3.473 ms
(39 rows)
```

### 데이터 몰림 2배 조건 · 후보 인덱스 실행계획

```text
QUERY PLAN
---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1.27..155.95 rows=10 width=138) (actual time=0.339..2.014 rows=10 loops=1)
   Output: c.id, a.id, a.title, u.id, u.nickname, c.content, ((SubPlan 1)), c.created_at
   Buffers: shared hit=218
   ->  Nested Loop  (cost=1.27..117708.37 rows=7610 width=138) (actual time=0.338..2.011 rows=10 loops=1)
         Output: c.id, a.id, a.title, u.id, u.nickname, c.content, (SubPlan 1), c.created_at
         Inner Unique: true
         Buffers: shared hit=218
         ->  Nested Loop  (cost=0.85..31489.12 rows=7610 width=103) (actual time=0.040..0.050 rows=10 loops=1)
               Output: c.id, c.content, c.created_at, c.article_id, u.id, u.nickname
               Buffers: shared hit=8
               ->  Index Scan using idx_comments_user_created_id on public.comments c  (cost=0.43..31385.56 rows=7610 width=88) (actual time=0.021..0.026 rows=10 loops=1)
                     Output: c.id, c.content, c.created_at, c.user_id, c.article_id
                     Index Cond: (c.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Filter: ((c.visibility_status)::text = 'ACTIVE'::text)
                     Buffers: shared hit=4
               ->  Materialize  (cost=0.42..8.44 rows=1 width=31) (actual time=0.002..0.002 rows=1 loops=10)
                     Output: u.id, u.nickname
                     Buffers: shared hit=4
                     ->  Index Scan using pk_users on public.users u  (cost=0.42..8.44 rows=1 width=31) (actual time=0.014..0.015 rows=1 loops=1)
                           Output: u.id, u.nickname
                           Index Cond: (u.id = '00000001-0000-4000-8000-000000000001'::uuid)
                           Buffers: shared hit=4
         ->  Index Scan using pk_articles on public.articles a  (cost=0.43..6.85 rows=1 width=43) (actual time=0.002..0.002 rows=1 loops=10)
               Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
               Index Cond: (a.id = c.article_id)
               Buffers: shared hit=40
         SubPlan 1
           ->  Aggregate  (cost=4.47..4.48 rows=1 width=8) (actual time=0.193..0.193 rows=1 loops=10)
                 Output: count(cl.id)
                 Buffers: shared hit=170
                 ->  Index Only Scan using idx_perf_comment_likes_active_comment_cover on public.comment_likes cl  (cost=0.43..4.46 rows=2 width=16) (actual time=0.005..0.132 rows=2001 loops=10)
                       Output: cl.comment_id, cl.id
                       Index Cond: (cl.comment_id = c.id)
                       Heap Fetches: 0
                       Buffers: shared hit=170
 Planning:
   Buffers: shared hit=404
 Planning Time: 0.955 ms
 Execution Time: 2.094 ms
(39 rows)
```

## 기술 근거와 개선 방향

- 후보 인덱스 적용 후 좋아요 집계가 `Index Only Scan`으로 바뀌었고 대표 실행계획의 `Heap Fetches`는 `0`이었다.
- 데이터 몰림 2배 조건에서 공유 버퍼는 `537`에서 `218`로 줄고 일반 SELECT 중앙값은 19.3% 개선됐다.
- 요청 한 번이 좋아요 수 집계를 위해 확인한 인덱스 항목은 데이터 몰림 조건 `10,010개`에서 2배 조건 `20,010개`로 늘었다. 인덱스는 실제 테이블 방문을 줄이지만 집계 항목 수와 결과별 하위 조회 반복은 줄이지 않는다.
