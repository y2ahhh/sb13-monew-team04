# 최근 좋아요한 댓글 실행계획

> [MID4-244 요약](README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

이 문서는 최근 좋아요한 댓글과 댓글별 활성 좋아요 수 조회에 같은 SQL을 사용하고, 기존 인덱스와 부분 커버링 인덱스를 비교한 결과다.

## 한눈에 보기

- 후보 인덱스가 공유 버퍼는 줄였지만 일반 SELECT 중앙값은 데이터 몰림 조건에서 14.6%, 2배 조건에서 9.9% 느려졌다.
- 집계 대상이 작아 이 조회만 보면 후보 인덱스의 적용 우선순위는 낮다.

## 실행 SQL

```sql
-- MID4-227 변경 반영 후: 최근 좋아요한 댓글과 댓글별 활성 좋아요 수를 조회합니다.
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

## 반복 측정

각 SQL을 3회 미리 실행한 뒤 일반 SELECT와 EXPLAIN을 각각 5회 측정했다. 아래 표의 실행 시간은 일반 SELECT 5회의 가운데 값이며 단위는 ms다. 개선율이 양수이면 후보 인덱스가 빨라진 것이다.

공유 버퍼는 아래 대표 실행계획의 `shared hit` 값이다. 워밍업 뒤 모든 대표 실행계획에서 디스크 읽기인 `shared read`는 `0`이었다.

| 데이터 조건 | 기존 인덱스 | 후보 인덱스 | 개선율 | 대표 실행계획 공유 버퍼(기존 → 후보) |
| --- | ---: | ---: | ---: | ---: |
| 데이터 몰림 조건 | 2.649 | 3.035 | -14.6% | 174 → 155 |
| 데이터 몰림 2배 조건 | 2.337 | 2.568 | -9.9% | 174 → 155 |

## 동일 조건 실행계획 원문

데이터 몰림 조건은 후보 인덱스를 사용한 실행계획을 기록했다. 데이터 몰림 2배 조건은 기존 인덱스와 후보 인덱스를 사용한 실행계획을 모두 기록했다. 실행계획의 `Execution Time`은 일반 SELECT 중앙값과 별도로 측정한 값이므로 서로 다를 수 있다.

### 데이터 몰림 조건 · 후보 인덱스 실행계획

```text
QUERY PLAN
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=1.70..167.56 rows=10 width=162) (actual time=0.082..0.123 rows=10 loops=1)
   Output: cl.id, cl.created_at, c.id, a.id, a.title, u.id, u.nickname, c.content, ((SubPlan 1)), c.created_at
   Buffers: shared hit=155
   ->  Nested Loop  (cost=1.70..146914.67 rows=8858 width=162) (actual time=0.080..0.121 rows=10 loops=1)
         Output: cl.id, cl.created_at, c.id, a.id, a.title, u.id, u.nickname, c.content, (SubPlan 1), c.created_at
         Inner Unique: true
         Buffers: shared hit=155
         ->  Nested Loop  (cost=1.28..102939.03 rows=8858 width=127) (actual time=0.043..0.064 rows=10 loops=1)
               Output: cl.id, cl.created_at, c.id, c.content, c.created_at, c.article_id, u.id, u.nickname
               Inner Unique: true
               Buffers: shared hit=84
               ->  Nested Loop  (cost=0.86..99068.77 rows=8858 width=112) (actual time=0.035..0.048 rows=10 loops=1)
                     Output: cl.id, cl.created_at, c.id, c.content, c.created_at, c.user_id, c.article_id
                     Inner Unique: true
                     Buffers: shared hit=44
                     ->  Index Scan using idx_comment_likes_liked_by_created_id on public.comment_likes cl  (cost=0.43..34248.82 rows=8858 width=40) (actual time=0.024..0.026 rows=10 loops=1)
                           Output: cl.id, cl.created_at, cl.comment_id
                           Index Cond: (cl.liked_by = '00000001-0000-4000-8000-000000000001'::uuid)
                           Filter: ((cl.visibility_status)::text = 'ACTIVE'::text)
                           Buffers: shared hit=4
                     ->  Index Scan using pk_comments on public.comments c  (cost=0.43..7.32 rows=1 width=88) (actual time=0.002..0.002 rows=1 loops=10)
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
           ->  Aggregate  (cost=4.45..4.46 rows=1 width=8) (actual time=0.003..0.003 rows=1 loops=10)
                 Output: count(cl2.id)
                 Buffers: shared hit=31
                 ->  Index Only Scan using idx_perf_comment_likes_active_comment_cover on public.comment_likes cl2  (cost=0.43..4.45 rows=1 width=16) (actual time=0.002..0.002 rows=2 loops=10)
                       Output: cl2.comment_id, cl2.id
                       Index Cond: (cl2.comment_id = c.id)
                       Heap Fetches: 0
                       Buffers: shared hit=31
 Planning:
   Buffers: shared hit=438
 Planning Time: 1.213 ms
 Execution Time: 0.177 ms
(45 rows)
```

### 데이터 몰림 2배 조건 · 기존 인덱스 실행계획

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

### 데이터 몰림 2배 조건 · 후보 인덱스 실행계획

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

## 기술 근거와 개선 방향

- 후보 인덱스 적용 후 좋아요 집계는 `Index Only Scan`과 `Heap Fetches 0`을 사용했고 공유 버퍼도 `174`에서 `155`로 줄었다.
- 그러나 일반 SELECT 중앙값은 데이터 몰림 조건에서 14.6%, 2배 조건에서 9.9% 느려져 이 조회에는 성능 이점이 확인되지 않았다.
- 집계 1회가 확인한 행이 `2개`이고 반복도 `10회`라 데이터 몰림 크기가 두 배가 되어도 집계 항목 수는 `20개`로 같았다. 이 조회만 보면 후보 인덱스의 우선순위는 낮다.
