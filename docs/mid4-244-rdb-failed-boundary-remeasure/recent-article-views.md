# 최근 조회 기사 실행계획

> [MID4-244 요약](README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

이 문서는 최근 조회 기사와 기사별 활성 댓글·조회 수 조회에 같은 SQL을 사용하고, 기존 인덱스와 부분 커버링 인덱스를 비교한 결과다.

## 한눈에 보기

- 부분 커버링 인덱스 적용 후 일반 SELECT 중앙값은 데이터 몰림 조건에서 56.0%, 2배 조건에서 56.9% 개선됐다.
- 네 조회 중 버퍼 감소 효과가 가장 크지만 기사별 댓글·조회 수를 반복 계산하는 비용은 남아 있다.
- 후속 확인에서 `count(*)`를 사용하면 `INCLUDE(id)` 없이도 부분 인덱스만으로 `Index Only Scan`이 가능했고, 기사 조회 인덱스 크기는 `144 MB`에서 `76 MB`로 줄었다.

## 실행 SQL

```sql
-- MID4-227 변경 반영 후: 최근 조회 기사와 기사별 활성 댓글·조회 수를 조회합니다.
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

## 반복 측정

각 SQL을 3회 미리 실행한 뒤 일반 SELECT와 EXPLAIN을 각각 5회 측정했다. 아래 표의 실행 시간은 일반 SELECT 5회의 가운데 값이며 단위는 ms다. 개선율이 양수이면 후보 인덱스가 빨라진 것이다.

공유 버퍼는 아래 대표 실행계획의 `shared hit` 값이다. 워밍업 뒤 모든 대표 실행계획에서 디스크 읽기인 `shared read`는 `0`이었다.

| 데이터 조건 | 기존 인덱스 | 후보 인덱스 | 개선율 | 대표 실행계획 공유 버퍼(기존 → 후보) |
| --- | ---: | ---: | ---: | ---: |
| 데이터 몰림 조건 | 17.793 | 7.826 | 56.0% | 2,456 → 806 |
| 데이터 몰림 2배 조건 | 34.868 | 15.035 | 56.9% | 7,863 → 1,474 |

## COUNT(*)와 INCLUDE(id) 비교

애플리케이션 코드를 바꾸지 않고 Hibernate `7.4.1.Final`의 임시 메타모델에서 JPQL 집계 표현이 생성하는 SQL을 확인했다. `COUNT(at2)`는 `count(별칭.id)`로, `COUNT(*)`는 그대로 `count(*)`로 변환됐다.

PostgreSQL `16.14`의 기존 데이터 몰림 DB에서는 활성 조회 `10,003건`이 연결된 기사 한 건을 대상으로 아래 두 인덱스를 하나씩 적용했다. 두 인덱스를 동시에 두지 않았고, 인덱스 생성 후 `ANALYZE`와 쿼리별 워밍업 3회를 수행한 다음 `EXPLAIN (ANALYZE, BUFFERS, TIMING OFF)`를 5회 측정했다.

```sql
-- INCLUDE(id) 없는 부분 인덱스
CREATE INDEX idx_check_article_views_active_article
    ON article_views(article_id)
    WHERE visibility_status = 'ACTIVE';

-- id까지 저장하는 부분 커버링 인덱스
CREATE INDEX idx_check_article_views_active_article_cover
    ON article_views(article_id) INCLUDE(id)
    WHERE visibility_status = 'ACTIVE';
```

실행 시간은 5회의 가운데 값이며 모든 측정에서 `shared read=0`이었다.

| 인덱스 | 크기 | 집계 | 실행계획 | Heap 접근 | shared hit | 실행 시간 중앙값 |
| --- | ---: | --- | --- | ---: | ---: | ---: |
| `INCLUDE(id)` 없음 | 76 MB | `count(id)` | Bitmap Heap Scan | Heap Blocks 127 | 138 | 0.849 ms |
| `INCLUDE(id)` 없음 | 76 MB | `count(*)` | Index Only Scan | Heap Fetches 0 | 13 | 0.369 ms |
| `INCLUDE(id)` 있음 | 144 MB | `count(id)` | Index Only Scan | Heap Fetches 0 | 65 | 0.516 ms |
| `INCLUDE(id)` 있음 | 144 MB | `count(*)` | Index Only Scan | Heap Fetches 0 | 65 | 0.493 ms |

현재 JPQL처럼 `COUNT(at2)`를 유지하면 집계에 필요한 `id`를 인덱스에서 읽도록 `INCLUDE(id)`가 필요하다. 반면 `COUNT(*)`는 `id` 값을 읽지 않으므로 `(article_id) WHERE visibility_status = 'ACTIVE'` 부분 인덱스만으로 필요한 정보를 모두 충족한다. 이 조건에서 `INCLUDE(id)`를 추가하면 인덱스 크기가 약 88.1% 증가했고 `count(*)`가 읽은 인덱스 블록도 13개에서 65개로 늘었다.

실행 시간은 한 로컬 DB의 워밍업된 단일 조건 결과이므로 운영 성능 보장값으로 사용하지 않는다. 이 비교의 주된 근거는 실행계획의 heap 접근 여부와 인덱스 크기다. 실험 인덱스는 측정 후 모두 삭제했으며 JPQL과 Flyway에는 반영하지 않았다.

## 동일 조건 실행계획 원문

데이터 몰림 조건은 후보 인덱스를 사용한 실행계획을 기록했다. 데이터 몰림 2배 조건은 기존 인덱스와 후보 인덱스를 사용한 실행계획을 모두 기록했다. 실행계획의 `Execution Time`은 일반 SELECT 중앙값과 별도로 측정한 값이므로 서로 다를 수 있다.

### 데이터 몰림 조건 · 후보 인덱스 실행계획

```text
QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.86..176.63 rows=10 width=206) (actual time=1.067..10.025 rows=10 loops=1)
   Output: av.id, av.user_id, av.viewed_at, a.id, a.source, a.link, a.title, a.date, a.summary, ((SubPlan 1)), ((SubPlan 2))
   Buffers: shared hit=806
   ->  Nested Loop  (cost=0.86..135413.31 rows=7704 width=206) (actual time=1.066..10.022 rows=10 loops=1)
         Output: av.id, av.user_id, av.viewed_at, a.id, a.source, a.link, a.title, a.date, a.summary, (SubPlan 1), (SubPlan 2)
         Inner Unique: true
         Buffers: shared hit=806
         ->  Index Scan using idx_article_views_user_viewed_id on public.article_views av  (cost=0.43..13258.95 rows=7704 width=56) (actual time=0.019..0.033 rows=10 loops=1)
               Output: av.id, av.user_id, av.viewed_at, av.article_id
               Index Cond: (av.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
               Filter: ((av.visibility_status)::text = 'ACTIVE'::text)
               Buffers: shared hit=4
         ->  Index Scan using pk_articles on public.articles a  (cost=0.43..6.84 rows=1 width=150) (actual time=0.005..0.005 rows=1 loops=10)
               Output: a.id, a.title, a.summary, a.link, a.date, a.source, a.created_at, a.updated_at, a.deleted_at
               Index Cond: (a.id = av.article_id)
               Buffers: shared hit=40
         SubPlan 1
           ->  Aggregate  (cost=4.47..4.48 rows=1 width=8) (actual time=0.097..0.097 rows=1 loops=10)
                 Output: count(c.id)
                 Buffers: shared hit=110
                 ->  Index Only Scan using idx_perf_comments_active_article_cover on public.comments c  (cost=0.43..4.46 rows=2 width=16) (actual time=0.008..0.066 rows=1002 loops=10)
                       Output: c.article_id, c.id
                       Index Cond: (c.article_id = a.id)
                       Heap Fetches: 0
                       Buffers: shared hit=110
         SubPlan 2
           ->  Aggregate  (cost=4.53..4.54 rows=1 width=8) (actual time=0.893..0.893 rows=1 loops=10)
                 Output: count(av2.id)
                 Buffers: shared hit=652
                 ->  Index Only Scan using idx_perf_article_views_active_article_cover on public.article_views av2  (cost=0.43..4.52 rows=5 width=16) (actual time=0.005..0.591 rows=10003 loops=10)
                       Output: av2.article_id, av2.id
                       Index Cond: (av2.article_id = a.id)
                       Heap Fetches: 0
                       Buffers: shared hit=652
 Planning:
   Buffers: shared hit=391
 Planning Time: 0.887 ms
 Execution Time: 10.081 ms
(38 rows)
```

### 데이터 몰림 2배 조건 · 기존 인덱스 실행계획

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

### 데이터 몰림 2배 조건 · 후보 인덱스 실행계획

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

## 기술 근거와 개선 방향

- 후보 인덱스 적용 후 기사별 댓글·조회 수 집계가 대부분 `Index Only Scan`으로 바뀌었고 공유 버퍼가 데이터 몰림 조건 `2,456`에서 `806`, 2배 조건 `7,863`에서 `1,474`로 줄었다.
- 데이터 몰림 2배 조건에서 일반 SELECT 중앙값은 56.9% 개선됐다. 다만 댓글 집계는 가시성 지도 상태 때문에 `Heap Fetches 10`이 기록됐고 조회 수 집계는 `0`이었다.
- 요청 한 번의 댓글 집계 항목은 `10,020개`에서 `20,020개`, 조회 수 집계 항목은 `100,030개`에서 `200,030개`로 늘었다. 인덱스가 실제 테이블 방문을 줄여도 정확한 개수를 세는 비용은 데이터 증가에 따라 커진다.
- 현재 `count(id)`를 유지한다면 부분 커버링 인덱스가 필요하지만, `count(*)`로 변경할 경우 기사 조회 수 집계에는 `INCLUDE(id)` 없는 ACTIVE 부분 인덱스가 더 작고 직접적인 후보다.
- `count(*)`와 인덱스 축소는 이번 확인 범위에서 구현하지 않았다. 실제 반영 전에는 기능 회귀 테스트와 조회·쓰기 부하를 다시 측정해야 한다.
