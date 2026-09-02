# 구독 관심사 실행계획

> [MID4-244 요약](README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

이 문서는 구독 관심사와 관심사별 활성 구독자 수 조회에 같은 SQL을 사용하고, 기존 인덱스와 부분 커버링 인덱스를 비교한 결과다.

## 한눈에 보기

- 부분 커버링 인덱스 적용 후 일반 SELECT 중앙값은 데이터 몰림 조건에서 49.6%, 2배 조건에서 46.7% 개선됐다.
- 관심사 50건마다 구독자 수를 계산하므로 데이터가 계속 늘면 집계 구조 자체의 개선이 필요하다.

## 실행 SQL

```sql
-- MID4-227 변경 반영 후: 구독 관심사와 관심사별 활성 구독자 수를 조회합니다.
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

## 반복 측정

각 SQL을 3회 미리 실행한 뒤 일반 SELECT와 EXPLAIN을 각각 5회 측정했다. 아래 표의 실행 시간은 일반 SELECT 5회의 가운데 값이며 단위는 ms다. 개선율이 양수이면 후보 인덱스가 빨라진 것이다.

공유 버퍼는 아래 대표 실행계획의 `shared hit` 값이다. 워밍업 뒤 모든 대표 실행계획에서 디스크 읽기인 `shared read`는 `0`이었다.

| 데이터 조건 | 기존 인덱스 | 후보 인덱스 | 개선율 | 대표 실행계획 공유 버퍼(기존 → 후보) |
| --- | ---: | ---: | ---: | ---: |
| 데이터 몰림 조건 | 9.319 | 4.693 | 49.6% | 1,944 → 614 |
| 데이터 몰림 2배 조건 | 13.635 | 7.269 | 46.7% | 3,123 → 910 |

## 동일 조건 실행계획 원문

데이터 몰림 조건은 후보 인덱스를 사용한 실행계획을 기록했다. 데이터 몰림 2배 조건은 기존 인덱스와 후보 인덱스를 사용한 실행계획을 모두 기록했다. 실행계획의 `Execution Time`은 일반 SELECT 중앙값과 별도로 측정한 값이므로 서로 다를 수 있다.

### 데이터 몰림 조건 · 후보 인덱스 실행계획

```text
QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=1690.15..1690.42 rows=109 width=78) (actual time=4.730..4.733 rows=50 loops=1)
   Output: s.id, s.created_at, i.id, i.created_at, i.name, i.updated_at, ((SubPlan 1))
   Sort Key: s.created_at DESC, s.id DESC
   Sort Method: quicksort  Memory: 30kB
   Buffers: shared hit=614
   ->  Nested Loop  (cost=9.57..1686.46 rows=109 width=78) (actual time=0.135..4.694 rows=50 loops=1)
         Output: s.id, s.created_at, i.id, i.created_at, i.name, i.updated_at, (SubPlan 1)
         Inner Unique: true
         Buffers: shared hit=608
         ->  Bitmap Heap Scan on public.subscriptions s  (cost=9.28..407.37 rows=109 width=40) (actual time=0.020..0.034 rows=50 loops=1)
               Output: s.id, s.created_at, s.interest_id
               Recheck Cond: (s.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
               Filter: ((s.visibility_status)::text = 'ACTIVE'::text)
               Heap Blocks: exact=1
               Buffers: shared hit=4
               ->  Bitmap Index Scan on idx_subscriptions_user_created_id  (cost=0.00..9.25 rows=110 width=0) (actual time=0.013..0.013 rows=50 loops=1)
                     Index Cond: (s.user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                     Buffers: shared hit=3
         ->  Index Scan using pk_interests on public.interests i  (cost=0.29..7.06 rows=1 width=46) (actual time=0.001..0.001 rows=1 loops=50)
               Output: i.id, i.name, i.created_at, i.updated_at
               Index Cond: (i.id = s.interest_id)
               Buffers: shared hit=150
         SubPlan 1
           ->  Aggregate  (cost=4.67..4.67 rows=1 width=8) (actual time=0.091..0.091 rows=1 loops=50)
                 Output: count(s2.id)
                 Buffers: shared hit=454
                 ->  Index Only Scan using idx_perf_subscriptions_active_interest_cover on public.subscriptions s2  (cost=0.42..4.63 rows=12 width=16) (actual time=0.002..0.061 rows=1011 loops=50)
                       Output: s2.interest_id, s2.id
                       Index Cond: (s2.interest_id = i.id)
                       Heap Fetches: 0
                       Buffers: shared hit=454
 Planning:
   Buffers: shared hit=259
 Planning Time: 0.653 ms
 Execution Time: 4.790 ms
(35 rows)
```

### 데이터 몰림 2배 조건 · 기존 인덱스 실행계획

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

### 데이터 몰림 2배 조건 · 후보 인덱스 실행계획

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

## 기술 근거와 개선 방향

- 후보 인덱스 적용 후 구독자 수 집계가 `Index Only Scan`으로 바뀌었고 대표 실행계획의 `Heap Fetches`는 `0`이었다.
- 공유 버퍼는 데이터 몰림 조건 `1,944`에서 `614`, 2배 조건 `3,123`에서 `910`으로 줄었고 일반 SELECT 중앙값은 각각 49.6%, 46.7% 개선됐다.
- 관심사 50건마다 집계를 반복하므로 요청 한 번이 확인한 인덱스 항목은 `50,550개`에서 `100,550개`로 늘었다. 데이터가 계속 증가하면 카운터 테이블이나 별도 조회 모델도 함께 검토해야 한다.
