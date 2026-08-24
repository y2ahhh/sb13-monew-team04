# 구독 중인 관심사

## 해석

구독 중인 관심사 조회는 `MID4-92`의 최종 조회 로직 기준으로 `subscriptions.created_at DESC, subscriptions.id DESC` 정렬을 포함한다. 대상 사용자의 구독은 50건이지만, main query에서 `subscriptions.user_id` 조건을 처리할 인덱스가 없어 scale이 커질수록 `Seq Scan` 또는 `Parallel Seq Scan`이 발생한다.

현재 `(interest_id, user_id)` unique index는 관심사별 구독자 수 subquery에는 사용되지만, 특정 사용자의 구독 목록을 찾는 main query에는 선두 컬럼이 맞지 않아 직접 사용되지 않는다. 따라서 main query는 `user_id` 접근 경로와 최신 구독순 정렬을 함께 처리할 수 있는 인덱스가 필요하다.

keywords batch 조회는 Hibernate가 PostgreSQL에서 `interest_id = any (?)` 형태로 실행한다. `uk_keywords_interest_keyword(interest_id, keyword)`는 조회 조건의 선두 컬럼과 맞으므로 `1m`, `10m`에서는 사용됐다. `100k`에서는 데이터가 작아 planner가 `Seq Scan`을 선택했다.

## 인덱스 후보

- 1차 후보: `subscriptions(user_id, created_at DESC, id DESC)`
- 위 후보는 대상 사용자의 구독 목록 접근과 `created_at DESC, id DESC` 정렬을 함께 해결하는지 확인한다.
- 비교 후보 A: `subscriptions(user_id)`를 별도로 적용해 full scan 제거 효과와 인덱스 용량 증가량을 비교한다.
- 비교 후보 B: `subscriptions(user_id, interest_id)`를 별도로 적용해 단순 FK 인덱스 대비 join 비용이나 heap 접근이 의미 있게 줄어드는지 비교한다.
- 정렬 대응 인덱스가 유의미하면 `subscriptions(user_id)` 또는 `subscriptions(user_id, interest_id)`처럼 역할이 겹치는 인덱스는 후속 정리 후보로 본다.
- 단, `uk_subscriptions_interest_user(interest_id, user_id)`는 중복 구독 방지와 관심사별 구독자 수 subquery에 필요하므로 제거 후보로 보지 않는다.
- keywords query는 현재 unique index의 선두 컬럼을 정상적으로 사용할 수 있으므로 신규 인덱스 후보로 두지 않는다.
- `users.deleted_at` 인덱스는 1차 후보로 두지 않고, `subscriptions.user_id` 접근 경로 개선 후 subquery 비용이 남는 경우 추가 측정한다.

## 측정 SQL

### 구독 중인 관심사

`MID4-92`의 `SubscribeRepository.findSubscribedInterestActivities()` JPQL을 임시 Spring Boot test에서 실행하고 Hibernate SQL 로그로 캡처한 실제 main query다. 아래 SQL은 측정을 위해 `userId` binding만 literal로 치환했다.

```sql
SELECT
    s1_0.id,
    s1_0.created_at,
    i1_0.id,
    i1_0.created_at,
    i1_0.name,
    i1_0.updated_at,
    (
        SELECT COUNT(s2_0.id)
        FROM subscriptions s2_0
        JOIN users u2_0 ON s2_0.user_id = u2_0.id
        JOIN interests i2_0 ON i2_0.id = s2_0.interest_id
        WHERE i2_0.id = i1_0.id
          AND u2_0.deleted_at IS NULL
    ) AS interest_subscriber_count
FROM subscriptions s1_0
JOIN interests i1_0 ON i1_0.id = s1_0.interest_id
JOIN users u1_0 ON u1_0.id = s1_0.user_id
WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001'
  AND u1_0.deleted_at IS NULL
ORDER BY s1_0.created_at DESC, s1_0.id DESC;
```

`SubscribedInterestActivity.from()`에서 `interest.getKeywords()`를 호출하므로 요청 1건 기준 키워드 batch 조회도 함께 측정했다. 실제 Hibernate SQL은 `IN` list가 아니라 PostgreSQL array binding인 `interest_id = any (?)` 형태로 실행됐다. binding 값은 `00000002-0000-4000-8000-000000000001`부터 `00000002-0000-4000-8000-000000000032`까지 50개 관심사 id다. 아래 SQL은 측정을 위해 같은 binding을 `ARRAY[...]` literal로 치환했다.

```sql
SELECT
    k1_0.interest_id,
    k1_0.id,
    k1_0.keyword
FROM keywords k1_0
WHERE k1_0.interest_id = ANY (ARRAY[
    '00000002-0000-4000-8000-000000000001'::uuid,
    '00000002-0000-4000-8000-000000000002'::uuid,
    '00000002-0000-4000-8000-000000000003'::uuid,
    '00000002-0000-4000-8000-000000000004'::uuid,
    '00000002-0000-4000-8000-000000000005'::uuid,
    '00000002-0000-4000-8000-000000000006'::uuid,
    '00000002-0000-4000-8000-000000000007'::uuid,
    '00000002-0000-4000-8000-000000000008'::uuid,
    '00000002-0000-4000-8000-000000000009'::uuid,
    '00000002-0000-4000-8000-00000000000a'::uuid,
    '00000002-0000-4000-8000-00000000000b'::uuid,
    '00000002-0000-4000-8000-00000000000c'::uuid,
    '00000002-0000-4000-8000-00000000000d'::uuid,
    '00000002-0000-4000-8000-00000000000e'::uuid,
    '00000002-0000-4000-8000-00000000000f'::uuid,
    '00000002-0000-4000-8000-000000000010'::uuid,
    '00000002-0000-4000-8000-000000000011'::uuid,
    '00000002-0000-4000-8000-000000000012'::uuid,
    '00000002-0000-4000-8000-000000000013'::uuid,
    '00000002-0000-4000-8000-000000000014'::uuid,
    '00000002-0000-4000-8000-000000000015'::uuid,
    '00000002-0000-4000-8000-000000000016'::uuid,
    '00000002-0000-4000-8000-000000000017'::uuid,
    '00000002-0000-4000-8000-000000000018'::uuid,
    '00000002-0000-4000-8000-000000000019'::uuid,
    '00000002-0000-4000-8000-00000000001a'::uuid,
    '00000002-0000-4000-8000-00000000001b'::uuid,
    '00000002-0000-4000-8000-00000000001c'::uuid,
    '00000002-0000-4000-8000-00000000001d'::uuid,
    '00000002-0000-4000-8000-00000000001e'::uuid,
    '00000002-0000-4000-8000-00000000001f'::uuid,
    '00000002-0000-4000-8000-000000000020'::uuid,
    '00000002-0000-4000-8000-000000000021'::uuid,
    '00000002-0000-4000-8000-000000000022'::uuid,
    '00000002-0000-4000-8000-000000000023'::uuid,
    '00000002-0000-4000-8000-000000000024'::uuid,
    '00000002-0000-4000-8000-000000000025'::uuid,
    '00000002-0000-4000-8000-000000000026'::uuid,
    '00000002-0000-4000-8000-000000000027'::uuid,
    '00000002-0000-4000-8000-000000000028'::uuid,
    '00000002-0000-4000-8000-000000000029'::uuid,
    '00000002-0000-4000-8000-00000000002a'::uuid,
    '00000002-0000-4000-8000-00000000002b'::uuid,
    '00000002-0000-4000-8000-00000000002c'::uuid,
    '00000002-0000-4000-8000-00000000002d'::uuid,
    '00000002-0000-4000-8000-00000000002e'::uuid,
    '00000002-0000-4000-8000-00000000002f'::uuid,
    '00000002-0000-4000-8000-000000000030'::uuid,
    '00000002-0000-4000-8000-000000000031'::uuid,
    '00000002-0000-4000-8000-000000000032'::uuid
]);
```

## 실행 시간

| Scale | 조회 | EXPLAIN Execution Time | 반복 실행 시간 5회 | Median |
| --- | --- | ---: | --- | ---: |
| `100k` | 구독 중인 관심사 main | `5.684 ms` | `3.969`, `4.629`, `4.421`, `4.624`, `3.963 ms` | `4.421 ms` |
| `100k` | 구독 관심사 keywords | `0.099 ms` | `0.562`, `0.467`, `0.708`, `0.640`, `0.399 ms` | `0.562 ms` |
| `100k` | 구독 중인 관심사 total | `-` | `4.423`, `5.391`, `4.600`, `4.491`, `4.812 ms` | `4.600 ms` |
| `1m` | 구독 중인 관심사 main | `3.736 ms` | `6.720`, `4.189`, `4.807`, `4.521`, `6.445 ms` | `4.807 ms` |
| `1m` | 구독 관심사 keywords | `0.160 ms` | `0.715`, `0.507`, `0.922`, `0.907`, `0.606 ms` | `0.715 ms` |
| `1m` | 구독 중인 관심사 total | `-` | `6.709`, `5.781`, `6.371`, `5.779`, `8.755 ms` | `6.371 ms` |
| `10m` | 구독 중인 관심사 main | `10.751 ms` | `12.189`, `10.978`, `11.564`, `10.824`, `10.946 ms` | `10.978 ms` |
| `10m` | 구독 관심사 keywords | `0.054 ms` | `0.590`, `0.570`, `1.003`, `0.605`, `0.859 ms` | `0.605 ms` |
| `10m` | 구독 중인 관심사 total | `-` | `13.093`, `11.224`, `11.573`, `11.310`, `11.226 ms` | `11.310 ms` |

## EXPLAIN 실행계획 요약

### 100k

구독 중인 관심사:

- main query는 `subscriptions.user_id` 조건을 `Seq Scan`으로 처리했다.
- `Rows Removed by Filter`: `4995`
- `ORDER BY s1_0.created_at DESC, s1_0.id DESC`는 `Sort Method: quicksort`로 처리했다.
- 관심사별 구독자 수 subquery는 `uk_subscriptions_interest_user`를 `interest_id = ...` 조건으로 사용했다.
- subquery 내부 활성 사용자 확인은 `users`를 50회 `Seq Scan`했다.
- keywords query는 `interest_id = any (...)` 조건을 `Seq Scan`으로 처리했다. 작은 scale에서는 planner가 index scan보다 sequential scan을 선택했다.

### 1m

구독 중인 관심사:

- main query는 `subscriptions.user_id` 조건을 `Seq Scan`으로 처리했다.
- `Rows Removed by Filter`: `49995`
- `ORDER BY s1_0.created_at DESC, s1_0.id DESC`는 `Sort Method: quicksort`로 처리했다.
- 관심사별 구독자 수 subquery는 `uk_subscriptions_interest_user`를 `interest_id = ...` 조건으로 사용했다.
- subquery 내부 활성 사용자 확인은 `pk_users`를 사용했다.
- keywords query는 `uk_keywords_interest_keyword`를 `interest_id = any (...)` 조건으로 사용했다.

### 10m

구독 중인 관심사:

- main query는 `subscriptions.user_id` 조건을 `Parallel Seq Scan`으로 처리했다.
- worker별 `Rows Removed by Filter`: `166665`, `loops=3`
- `ORDER BY s1_0.created_at DESC, s1_0.id DESC`는 worker별 `Sort`와 `Gather Merge`로 처리했다.
- 관심사별 구독자 수 subquery는 `uk_subscriptions_interest_user`를 `interest_id = ...` 조건으로 사용했다.
- keywords query는 `uk_keywords_interest_keyword`를 `interest_id = any (...)` 조건으로 사용했다.
- 요청 1건 기준 total median은 `11.310 ms`다. 현재 scale에서는 다른 최근 활동 조회보다 낮지만, main query가 전체 subscriptions를 훑는 구조라 데이터 증가 시 병목 후보로 남는다.

## EXPLAIN 실행계획 주요 원문

아래 원문에서 keywords의 `ANY` 배열은 가독성을 위해 중간 값을 생략했다. 실제 측정은 50개 관심사 id 전체를 binding한 SQL로 수행했다.

<details>
<summary>100k - 구독 중인 관심사</summary>

main query:

```text
Sort  (cost=3568.96..3569.09 rows=50 width=76) (actual time=5.643..5.646 rows=50 loops=1)
  Sort Key: s1_0.created_at DESC, s1_0.id DESC
  Sort Method: quicksort  Memory: 30kB
  Buffers: shared hit=1607
  ->  Nested Loop  (cost=111.96..3567.55 rows=50 width=76) (actual time=0.446..5.610 rows=50 loops=1)
        ->  Index Scan using pk_users on users u1_0
              Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
              Filter: (deleted_at IS NULL)
        ->  Hash Join
              Hash Cond: (i1_0.id = s1_0.interest_id)
              ->  Seq Scan on interests i1_0
              ->  Hash
                    ->  Seq Scan on subscriptions s1_0
                          Filter: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                          Rows Removed by Filter: 4995
  SubPlan 1
    ->  Aggregate  (cost=67.94..67.95 rows=1 width=8) (actual time=0.104..0.104 rows=1 loops=50)
          Buffers: shared hit=1546
          ->  Nested Loop
                ->  Index Only Scan using pk_interests on interests i2_0
                ->  Hash Join
                      Hash Cond: (u2_0.id = s2_0.user_id)
                      ->  Seq Scan on users u2_0
                            Filter: (deleted_at IS NULL)
                            Rows Removed by Filter: 10
                      ->  Hash
                            ->  Bitmap Heap Scan on subscriptions s2_0
                                  Recheck Cond: (interest_id = i1_0.id)
                                  ->  Bitmap Index Scan on uk_subscriptions_interest_user
                                        Index Cond: (interest_id = i1_0.id)
Planning Time: 0.790 ms
Execution Time: 5.684 ms
```

keywords query:

```text
Seq Scan on keywords k1_0  (cost=0.12..37.62 rows=150 width=45) (actual time=0.008..0.081 rows=150 loops=1)
  Filter: (interest_id = ANY ('{00000002-0000-4000-8000-000000000001,...,00000002-0000-4000-8000-000000000032}'::uuid[]))
  Rows Removed by Filter: 1350
  Buffers: shared hit=15
Planning Time: 0.541 ms
Execution Time: 0.099 ms
```

</details>

<details>
<summary>1m - 구독 중인 관심사</summary>

main query:

```text
Sort  (cost=7657.67..7657.80 rows=50 width=77) (actual time=3.666..3.673 rows=50 loops=1)
  Sort Key: s1_0.created_at DESC, s1_0.id DESC
  Sort Method: quicksort  Memory: 30kB
  Buffers: shared hit=2950
  ->  Nested Loop  (cost=1094.66..7656.26 rows=50 width=77) (actual time=2.579..3.626 rows=50 loops=1)
        ->  Index Scan using pk_users on users u1_0
              Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
              Filter: (deleted_at IS NULL)
        ->  Hash Join
              Hash Cond: (i1_0.id = s1_0.interest_id)
              ->  Seq Scan on interests i1_0
              ->  Hash
                    ->  Seq Scan on subscriptions s1_0
                          Filter: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                          Rows Removed by Filter: 49995
  SubPlan 1
    ->  Aggregate  (cost=127.68..127.69 rows=1 width=8) (actual time=0.015..0.015 rows=1 loops=50)
          Buffers: shared hit=2431
          ->  Nested Loop
                ->  Index Only Scan using pk_interests on interests i2_0
                ->  Nested Loop
                      ->  Bitmap Heap Scan on subscriptions s2_0
                            Recheck Cond: (interest_id = i1_0.id)
                            ->  Bitmap Index Scan on uk_subscriptions_interest_user
                                  Index Cond: (interest_id = i1_0.id)
                      ->  Index Scan using pk_users on users u2_0
                            Index Cond: (id = s2_0.user_id)
                            Filter: (deleted_at IS NULL)
Planning Time: 0.764 ms
Execution Time: 3.736 ms
```

keywords query:

```text
Bitmap Heap Scan on keywords k1_0  (cost=183.54..332.68 rows=150 width=46) (actual time=0.083..0.119 rows=150 loops=1)
  Recheck Cond: (interest_id = ANY ('{00000002-0000-4000-8000-000000000001,...,00000002-0000-4000-8000-000000000032}'::uuid[]))
  Heap Blocks: exact=4
  Buffers: shared hit=104
  ->  Bitmap Index Scan on uk_keywords_interest_keyword  (cost=0.00..183.38 rows=150 width=0) (actual time=0.071..0.071 rows=150 loops=1)
        Index Cond: (interest_id = ANY ('{00000002-0000-4000-8000-000000000001,...,00000002-0000-4000-8000-000000000032}'::uuid[]))
Planning Time: 0.395 ms
Execution Time: 0.160 ms
```

</details>

<details>
<summary>10m - 구독 중인 관심사</summary>

main query:

```text
Nested Loop  (cost=9062.17..22802.62 rows=100 width=78) (actual time=8.751..10.690 rows=50 loops=1)
  Buffers: shared hit=3570 read=4282
  ->  Index Scan using pk_users on users u1_0
        Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
        Filter: (deleted_at IS NULL)
  ->  Gather Merge  (cost=9061.75..9073.40 rows=100 width=86) (actual time=8.624..9.876 rows=50 loops=1)
        Workers Planned: 2
        Workers Launched: 2
        ->  Sort
              Sort Key: s1_0.created_at DESC, s1_0.id DESC
              Sort Method: quicksort  Memory: 30kB
              Worker 0:  Sort Method: quicksort  Memory: 25kB
              Worker 1:  Sort Method: quicksort  Memory: 25kB
              ->  Parallel Seq Scan on subscriptions s1_0
                    Filter: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Rows Removed by Filter: 166665
  SubPlan 1
    ->  Aggregate  (actual time=0.014..0.015 rows=1 loops=50)
          ->  Nested Loop
                ->  Index Only Scan using pk_interests on interests i2_0
                ->  Nested Loop
                      ->  Index Scan using uk_subscriptions_interest_user on subscriptions s2_0
                            Index Cond: (interest_id = i1_0.id)
                      ->  Index Scan using pk_users on users u2_0
                            Index Cond: (id = s2_0.user_id)
                            Filter: (deleted_at IS NULL)
Planning Time: 0.788 ms
Execution Time: 10.751 ms
```

keywords query:

```text
Bitmap Heap Scan on keywords k1_0  (cost=218.29..655.53 rows=150 width=47) (actual time=0.023..0.037 rows=150 loops=1)
  Recheck Cond: (interest_id = ANY ('{00000002-0000-4000-8000-000000000001,...,00000002-0000-4000-8000-000000000032}'::uuid[]))
  Heap Blocks: exact=4
  Buffers: shared hit=154
  ->  Bitmap Index Scan on uk_keywords_interest_keyword  (cost=0.00..218.12 rows=150 width=0) (actual time=0.018..0.018 rows=150 loops=1)
        Index Cond: (interest_id = ANY ('{00000002-0000-4000-8000-000000000001,...,00000002-0000-4000-8000-000000000032}'::uuid[]))
Planning Time: 0.359 ms
Execution Time: 0.054 ms
```

</details>
