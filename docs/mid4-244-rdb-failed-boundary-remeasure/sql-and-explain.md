# MID4-244 실제 SQL과 실행계획 원문

> [재측정 결과로 돌아가기](README.md)

아래 SQL은 애플리케이션이 실행한 조회를 PostgreSQL 문법으로 옮긴 MID4-227 이후 SQL이다. 이번 작업에서는 SQL을 변경하지 않고 같은 SQL에 부분 커버링 인덱스(partial covering index)를 적용하기 전과 후를 비교했다.

실행계획은 연결 데이터 2배 조건에서 워밍업 3회 뒤 출력한 원문이다. 표로 바꾸거나 일부 단계만 잘라내지 않았다. 실행계획을 저장하기 위한 실행은 `EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS, TIMING)`을 사용했다.

## 최근 작성 댓글

### 실제 실행 SQL

```sql
-- MID4-227 after: 최근 작성 댓글과 댓글별 활성 좋아요 수를 조회합니다.
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

### 현재 인덱스 실행계획

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

### 부분 커버링 인덱스 실행계획

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

## 최근 좋아요한 댓글

### 실제 실행 SQL

```sql
-- MID4-227 after: 최근 좋아요한 댓글과 댓글별 활성 좋아요 수를 조회합니다.
-- CommentLikeRepository.findRecentCommentLikeActivity의 PostgreSQL 대응 SQL입니다.
SELECT
    cl.id,
    cl.created_at,
    c.id AS comment_id,
    a.id AS article_id,
    a.title AS article_title,
    u.id AS comment_user_id,
    u.nickname AS comment_user_nickname,
    c.content AS comment_content,
    (
        SELECT count(cl2.id)
        FROM comment_likes cl2
        WHERE cl2.comment_id = c.id
          AND cl2.visibility_status = 'ACTIVE'
    ) AS comment_like_count,
    c.created_at AS comment_created_at
FROM comment_likes cl
JOIN comments c ON c.id = cl.comment_id
JOIN users u ON u.id = c.user_id
JOIN articles a ON a.id = c.article_id
WHERE cl.liked_by = '00000001-0000-4000-8000-000000000001'
  AND cl.visibility_status = 'ACTIVE'
ORDER BY cl.created_at DESC, cl.id DESC
FETCH FIRST 10 ROWS ONLY;
```

### 현재 인덱스 실행계획

```text
QUERY PLAN
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1.70..231.52 rows=10 width=162) (actual time=0.061..0.109 rows=10 loops=1)
   Output: cl.id, cl.created_at, c.id, a.id, a.title, u.id, u.nickname, c.content, ((SubPlan 1)), c.created_at
   Buffers: shared hit=174
   ->  Nested Loop  (cost=1.70..212970.37 rows=9267 width=162) (actual time=0.060..0.107 rows=10 loops=1)
         Output: cl.id, cl.created_at, c.id, a.id, a.title, u.id, u.nickname, c.content, (SubPlan 1), c.created_at
         Inner Unique: true
         Buffers: shared hit=174
         ->  Nested Loop  (cost=1.28..107154.26 rows=9267 width=127) (actual time=0.034..0.055 rows=10 loops=1)
               Output: cl.id, cl.created_at, c.id, c.content, c.created_at, c.article_id, u.id, u.nickname
               Inner Unique: true
               Buffers: shared hit=84
               ->  Nested Loop  (cost=0.86..103105.35 rows=9267 width=112) (actual time=0.027..0.039 rows=10 loops=1)
                     Output: cl.id, cl.created_at, c.id, c.content, c.created_at, c.user_id, c.article_id
                     Inner Unique: true
                     Buffers: shared hit=44
                     ->  Index Scan using idx_comment_likes_liked_by_created_id on public.comment_likes cl  (cost=0.43..35638.36 rows=9267 width=40) (actual time=0.017..0.019 rows=10 loops=1)
                           Output: cl.id, cl.created_at, cl.comment_id
                           Index Cond: (cl.liked_by = '00000001-0000-4000-8000-000000000001'::uuid)
                           Filter: ((cl.visibility_status)::text = 'ACTIVE'::text)
                           Buffers: shared hit=4
                     ->  Index Scan using pk_comments on public.comments c  (cost=0.43..7.28 rows=1 width=88) (actual time=0.002..0.002 rows=1 loops=10)
                           Output: c.id, c.content, c.created_at, c.user_id, c.article_id
                           Index Cond: (c.id = cl.comment_id)
                           Buffers: shared hit=40
               ->  Index Scan using pk_users on public.users u  (cost=0.42..0.44 rows=1 width=31) (actual time=0.001..0.001 rows=1 loops=10)
                     Output: u.id, u.email, u.nickname, u.password, u.created_at, u.updated_at, u.deleted_at
                     Index Cond: (u.id = c.user_id)
                     Buffers: shared hit=40
         ->  Index Scan using pk_articles on public.articles a  (cost=0.43..0.50 rows=1 width=43) (actual time=0.002..0.002 rows=1 loops=10)
               Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
               Index Cond: (a.id = c.article_id)
               Buffers: shared hit=40
         SubPlan 1
           ->  Aggregate  (cost=10.90..10.91 rows=1 width=8) (actual time=0.003..0.003 rows=1 loops=10)
                 Output: count(cl2.id)
                 Buffers: shared hit=50
                 ->  Index Scan using uk_comment_likes_comment_liked_by on public.comment_likes cl2  (cost=0.43..10.90 rows=1 width=16) (actual time=0.002..0.002 rows=2 loops=10)
                       Output: cl2.id
                       Index Cond: (cl2.comment_id = c.id)
                       Filter: ((cl2.visibility_status)::text = 'ACTIVE'::text)
                       Buffers: shared hit=50
 Planning:
   Buffers: shared hit=408
 Planning Time: 1.266 ms
 Execution Time: 0.179 ms
(45 rows)
```

### 부분 커버링 인덱스 실행계획

```text
QUERY PLAN
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1.70..167.57 rows=10 width=162) (actual time=0.067..0.108 rows=10 loops=1)
   Output: cl.id, cl.created_at, c.id, a.id, a.title, u.id, u.nickname, c.content, ((SubPlan 1)), c.created_at
   Buffers: shared hit=155
   ->  Nested Loop  (cost=1.70..149128.18 rows=8991 width=162) (actual time=0.066..0.106 rows=10 loops=1)
         Output: cl.id, cl.created_at, c.id, a.id, a.title, u.id, u.nickname, c.content, (SubPlan 1), c.created_at
         Inner Unique: true
         Buffers: shared hit=155
         ->  Nested Loop  (cost=1.28..104313.80 rows=8991 width=127) (actual time=0.037..0.058 rows=10 loops=1)
               Output: cl.id, cl.created_at, c.id, c.content, c.created_at, c.article_id, u.id, u.nickname
               Inner Unique: true
               Buffers: shared hit=84
               ->  Nested Loop  (cost=0.86..100385.48 rows=8991 width=112) (actual time=0.028..0.041 rows=10 loops=1)
                     Output: cl.id, cl.created_at, c.id, c.content, c.created_at, c.user_id, c.article_id
                     Inner Unique: true
                     Buffers: shared hit=44
                     ->  Index Scan using idx_comment_likes_liked_by_created_id on public.comment_likes cl  (cost=0.43..34678.01 rows=8991 width=40) (actual time=0.017..0.020 rows=10 loops=1)
                           Output: cl.id, cl.created_at, cl.comment_id
                           Index Cond: (cl.liked_by = '00000001-0000-4000-8000-000000000001'::uuid)
                           Filter: ((cl.visibility_status)::text = 'ACTIVE'::text)
                           Buffers: shared hit=4
                     ->  Index Scan using pk_comments on public.comments c  (cost=0.43..7.31 rows=1 width=88) (actual time=0.002..0.002 rows=1 loops=10)
                           Output: c.id, c.content, c.created_at, c.user_id, c.article_id
                           Index Cond: (c.id = cl.comment_id)
                           Buffers: shared hit=40
               ->  Index Scan using pk_users on public.users u  (cost=0.42..0.44 rows=1 width=31) (actual time=0.002..0.002 rows=1 loops=10)
                     Output: u.id, u.email, u.nickname, u.password, u.created_at, u.updated_at, u.deleted_at
                     Index Cond: (u.id = c.user_id)
                     Buffers: shared hit=40
         ->  Index Scan using pk_articles on public.articles a  (cost=0.43..0.50 rows=1 width=43) (actual time=0.001..0.001 rows=1 loops=10)
               Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
               Index Cond: (a.id = c.article_id)
               Buffers: shared hit=40
         SubPlan 1
           ->  Aggregate  (cost=4.47..4.48 rows=1 width=8) (actual time=0.003..0.003 rows=1 loops=10)
                 Output: count(cl2.id)
                 Buffers: shared hit=31
                 ->  Index Only Scan using idx_perf_comment_likes_active_comment_cover on public.comment_likes cl2  (cost=0.43..4.46 rows=2 width=16) (actual time=0.002..0.002 rows=2 loops=10)
                       Output: cl2.comment_id, cl2.id
                       Index Cond: (cl2.comment_id = c.id)
                       Heap Fetches: 0
                       Buffers: shared hit=31
 Planning:
   Buffers: shared hit=438
 Planning Time: 1.553 ms
 Execution Time: 0.167 ms
(45 rows)
```

## 최근 조회 기사

### 실제 실행 SQL

```sql
-- MID4-227 after: 최근 조회 기사와 기사별 활성 댓글·조회 수를 조회합니다.
-- ArticleViewRepository.findRecentArticleViewActivities의 PostgreSQL 대응 SQL입니다.
SELECT
    av.id,
    av.user_id AS viewed_by,
    av.viewed_at,
    a.id AS article_id,
    a.source,
    a.link,
    a.title,
    a.date,
    a.summary,
    (
        SELECT count(c.id)
        FROM comments c
        WHERE c.article_id = a.id
          AND c.visibility_status = 'ACTIVE'
    ) AS article_comment_count,
    (
        SELECT count(av2.id)
        FROM article_views av2
        WHERE av2.article_id = a.id
          AND av2.visibility_status = 'ACTIVE'
    ) AS article_view_count
FROM article_views av
JOIN articles a ON a.id = av.article_id
WHERE av.user_id = '00000001-0000-4000-8000-000000000001'
  AND av.visibility_status = 'ACTIVE'
ORDER BY av.viewed_at DESC, av.id DESC
FETCH FIRST 10 ROWS ONLY;
```

### 현재 인덱스 실행계획

```text
QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.86..644.93 rows=10 width=206) (actual time=5.002..40.127 rows=10 loops=1)
   Output: av.id, av.user_id, av.viewed_at, a.id, a.source, a.link, a.title, a.date, a.summary, ((SubPlan 1)), ((SubPlan 2))
   Buffers: shared hit=7863
   ->  Nested Loop  (cost=0.86..479321.07 rows=7442 width=206) (actual time=5.001..40.122 rows=10 loops=1)
         Output: av.id, av.user_id, av.viewed_at, a.id, a.source, a.link, a.title, a.date, a.summary, (SubPlan 1), (SubPlan 2)
         Inner Unique: true
         Buffers: shared hit=7863
         ->  Index Scan using idx_article_views_user_viewed_id on public.article_views av  (cost=0.43..12836.98 rows=7442 width=56) (actual time=0.017..0.025 rows=10 loops=1)
               Output: av.id, av.user_id, av.viewed_at, av.article_id
               Index Cond: (av.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
               Filter: ((av.visibility_status)::text = 'ACTIVE'::text)
               Buffers: shared hit=4
         ->  Index Scan using pk_articles on public.articles a  (cost=0.43..6.88 rows=1 width=150) (actual time=0.004..0.004 rows=1 loops=10)
               Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
               Index Cond: (a.id = av.article_id)
               Buffers: shared hit=40
         SubPlan 1
           ->  Aggregate  (cost=15.16..15.17 rows=1 width=8) (actual time=0.734..0.734 rows=1 loops=10)
                 Output: count(c.id)
                 Buffers: shared hit=3520
                 ->  Index Scan using idx_comments_article on public.comments c  (cost=0.43..15.15 rows=3 width=16) (actual time=0.009..0.670 rows=2002 loops=10)
                       Output: c.id
                       Index Cond: (c.article_id = a.id)
                       Filter: ((c.visibility_status)::text = 'ACTIVE'::text)
                       Buffers: shared hit=3520
         SubPlan 2
           ->  Aggregate  (cost=40.63..40.64 rows=1 width=8) (actual time=3.265..3.266 rows=1 loops=10)
                 Output: count(av2.id)
                 Buffers: shared hit=4299
                 ->  Index Scan using idx_article_views_article_viewed on public.article_views av2  (cost=0.43..40.60 rows=9 width=16) (actual time=0.008..2.629 rows=20003 loops=10)
                       Output: av2.id
                       Index Cond: (av2.article_id = a.id)
                       Filter: ((av2.visibility_status)::text = 'ACTIVE'::text)
                       Buffers: shared hit=4299
 Planning:
   Buffers: shared hit=361
 Planning Time: 0.915 ms
 Execution Time: 40.193 ms
(38 rows)
```

### 부분 커버링 인덱스 실행계획

```text
QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.86..278.22 rows=10 width=206) (actual time=2.192..21.826 rows=10 loops=1)
   Output: av.id, av.user_id, av.viewed_at, a.id, a.source, a.link, a.title, a.date, a.summary, ((SubPlan 1)), ((SubPlan 2))
   Buffers: shared hit=1474
   ->  Nested Loop  (cost=0.86..310511.99 rows=11195 width=206) (actual time=2.191..21.822 rows=10 loops=1)
         Output: av.id, av.user_id, av.viewed_at, a.id, a.source, a.link, a.title, a.date, a.summary, (SubPlan 1), (SubPlan 2)
         Inner Unique: true
         Buffers: shared hit=1474
         ->  Index Scan using idx_article_views_user_viewed_id on public.article_views av  (cost=0.43..18596.56 rows=11195 width=56) (actual time=0.018..0.033 rows=10 loops=1)
               Output: av.id, av.user_id, av.viewed_at, av.article_id
               Index Cond: (av.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
               Filter: ((av.visibility_status)::text = 'ACTIVE'::text)
               Buffers: shared hit=4
         ->  Index Scan using pk_articles on public.articles a  (cost=0.43..6.37 rows=1 width=150) (actual time=0.004..0.004 rows=1 loops=10)
               Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
               Index Cond: (a.id = av.article_id)
               Buffers: shared hit=40
         SubPlan 1
           ->  Aggregate  (cost=15.07..15.08 rows=1 width=8) (actual time=0.221..0.221 rows=1 loops=10)
                 Output: count(c.id)
                 Buffers: shared hit=180
                 ->  Index Only Scan using idx_perf_comments_active_article_cover on public.comments c  (cost=0.43..15.07 rows=3 width=16) (actual time=0.007..0.152 rows=2002 loops=10)
                       Output: c.article_id, c.id
                       Index Cond: (c.article_id = a.id)
                       Heap Fetches: 10
                       Buffers: shared hit=180
         SubPlan 2
           ->  Aggregate  (cost=4.61..4.62 rows=1 width=8) (actual time=1.949..1.949 rows=1 loops=10)
                 Output: count(av2.id)
                 Buffers: shared hit=1250
                 ->  Index Only Scan using idx_perf_article_views_active_article_cover on public.article_views av2  (cost=0.43..4.59 rows=9 width=16) (actual time=0.012..1.326 rows=20003 loops=10)
                       Output: av2.article_id, av2.id
                       Index Cond: (av2.article_id = a.id)
                       Heap Fetches: 0
                       Buffers: shared hit=1250
 Planning:
   Buffers: shared hit=391
 Planning Time: 0.843 ms
 Execution Time: 21.896 ms
(38 rows)
```

## 구독 관심사

### 실제 실행 SQL

```sql
-- MID4-227 after: 구독 관심사와 관심사별 활성 구독자 수를 조회합니다.
-- SubscribeRepository.findSubscribedInterestActivities의 PostgreSQL 대응 SQL입니다.
SELECT
    s.id,
    s.created_at,
    i.id AS interest_id,
    i.created_at AS interest_created_at,
    i.name,
    i.updated_at AS interest_updated_at,
    (
        SELECT count(s2.id)
        FROM subscriptions s2
        WHERE s2.interest_id = i.id
          AND s2.visibility_status = 'ACTIVE'
    ) AS subscriber_count
FROM subscriptions s
JOIN interests i ON i.id = s.interest_id
WHERE s.user_id = '00000001-0000-4000-8000-000000000001'
  AND s.visibility_status = 'ACTIVE'
ORDER BY s.created_at DESC, s.id DESC;
```

### 현재 인덱스 실행계획

```text
QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Nested Loop  (cost=0.71..659.53 rows=9 width=78) (actual time=0.394..16.066 rows=50 loops=1)
   Output: s.id, s.created_at, i.id, i.created_at, i.name, i.updated_at, (SubPlan 1)
   Inner Unique: true
   Buffers: shared hit=3123
   ->  Index Scan using idx_subscriptions_user_created_id on public.subscriptions s  (cost=0.42..38.99 rows=9 width=40) (actual time=0.017..0.031 rows=50 loops=1)
         Output: s.id, s.created_at, s.interest_id
         Index Cond: (s.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
         Filter: ((s.visibility_status)::text = 'ACTIVE'::text)
         Buffers: shared hit=4
   ->  Index Scan using pk_interests on public.interests i  (cost=0.29..8.31 rows=1 width=46) (actual time=0.001..0.001 rows=1 loops=50)
         Output: i.id, i.name, i.created_at, i.updated_at
         Index Cond: (i.id = s.interest_id)
         Buffers: shared hit=150
   SubPlan 1
     ->  Aggregate  (cost=60.63..60.64 rows=1 width=8) (actual time=0.318..0.318 rows=1 loops=50)
           Output: count(s2.id)
           Buffers: shared hit=2969
           ->  Index Scan using uk_subscriptions_interest_user on public.subscriptions s2  (cost=0.42..60.59 rows=15 width=16) (actual time=0.006..0.254 rows=2011 loops=50)
                 Output: s2.id
                 Index Cond: (s2.interest_id = i.id)
                 Filter: ((s2.visibility_status)::text = 'ACTIVE'::text)
                 Rows Removed by Filter: 0
                 Buffers: shared hit=2969
 Planning:
   Buffers: shared hit=244
 Planning Time: 0.578 ms
 Execution Time: 16.118 ms
(27 rows)
```

### 부분 커버링 인덱스 실행계획

```text
QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Nested Loop  (cost=0.71..156.57 rows=9 width=78) (actual time=0.225..9.413 rows=50 loops=1)
   Output: s.id, s.created_at, i.id, i.created_at, i.name, i.updated_at, (SubPlan 1)
   Inner Unique: true
   Buffers: shared hit=910
   ->  Index Scan using idx_subscriptions_user_created_id on public.subscriptions s  (cost=0.42..39.19 rows=9 width=40) (actual time=0.017..0.038 rows=50 loops=1)
         Output: s.id, s.created_at, s.interest_id
         Index Cond: (s.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
         Filter: ((s.visibility_status)::text = 'ACTIVE'::text)
         Buffers: shared hit=4
   ->  Index Scan using pk_interests on public.interests i  (cost=0.29..8.31 rows=1 width=46) (actual time=0.001..0.001 rows=1 loops=50)
         Output: i.id, i.name, i.created_at, i.updated_at
         Index Cond: (i.id = s.interest_id)
         Buffers: shared hit=150
   SubPlan 1
     ->  Aggregate  (cost=4.72..4.73 rows=1 width=8) (actual time=0.186..0.186 rows=1 loops=50)
           Output: count(s2.id)
           Buffers: shared hit=756
           ->  Index Only Scan using idx_perf_subscriptions_active_interest_cover on public.subscriptions s2  (cost=0.42..4.69 rows=15 width=16) (actual time=0.004..0.125 rows=2011 loops=50)
                 Output: s2.interest_id, s2.id
                 Index Cond: (s2.interest_id = i.id)
                 Heap Fetches: 0
                 Buffers: shared hit=756
 Planning:
   Buffers: shared hit=259
 Planning Time: 0.642 ms
 Execution Time: 9.462 ms
(26 rows)
```

## 실행계획에서 확인한 공통점

- 부분 인덱스 적용 후 집계 경로가 인덱스만 읽는 방식(Index Only Scan)으로 바뀌었다.
- `Heap Fetches`는 대부분 `0`이었다. 최근 조회 기사의 댓글 집계 원문에서는 가시성 지도(visibility map) 상태 때문에 `10`이 기록됐다.
- 워밍업 뒤 대표 실행계획의 디스크 읽기(`shared read`)는 `0`이므로 최초 디스크 읽기 시간은 비교값에 포함되지 않았다.
- 커버링 인덱스는 실제 테이블 방문을 줄이지만, 조건에 맞는 인덱스 항목의 개수를 세는 작업과 결과별 하위 조회 반복은 그대로 유지된다.
