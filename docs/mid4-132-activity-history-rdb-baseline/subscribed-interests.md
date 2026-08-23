# 구독 중인 관심사

## 해석

구독 중인 관심사 조회는 50건 전체 반환이라 정렬 비용은 없지만, main query에서 `subscriptions.user_id` 조건을 처리할 인덱스가 없다. 현재 `(interest_id, user_id)` unique index는 관심사별 구독자 수 subquery에는 쓰이지만, 특정 사용자의 구독 목록을 찾는 main query에는 직접 쓰이지 않는다.

현재 baseline은 `subscriptions.created_at` 컬럼이 없고 `ORDER BY` 없이 50건 전체를 반환하는 SQL 기준이다. 후속으로 구독 생성일자 컬럼이 추가되고 `created_at DESC, id DESC` 정렬이 들어오면 현재 후보를 그대로 확정하지 않고 정렬 기준까지 포함해 다시 비교한다.

## 인덱스 후보

- 후보 A: `subscriptions(user_id)` FK USER 조회 인덱스를 하나씩 적용해 `Seq Scan` 또는 `Parallel Seq Scan` 제거 여부를 확인한다.
- 후보 B: `subscriptions(user_id, interest_id)` 복합 인덱스를 별도로 적용해 같은 방식으로 비교한다.
- 두 후보 모두 main query 문제를 해결하면 `pg_relation_size(index_name)`와 `pg_indexes_size('subscriptions')`로 인덱스 용량 증가량을 비교한다.
- 복합 인덱스는 단일 FK 인덱스 대비 실행 시간, heap 접근, join 비용이 의미 있게 줄어들 때만 우선 후보로 본다.
- 후속 정렬 도입 후 후보는 `subscriptions(user_id, created_at DESC, id DESC)`로 둔다. 이 후보가 `user_id` 접근과 최신 구독순 정렬을 함께 해결하면 `subscriptions(user_id)` 또는 `subscriptions(user_id, interest_id)`는 용량과 쓰기 비용 관점에서 제거 후보로 본다.
- 단, `uk_subscriptions_interest_user(interest_id, user_id)`는 중복 구독 방지와 관심사별 구독자 수 subquery에 필요하므로 제거 후보로 보지 않는다.
- keywords query는 실제 Hibernate SQL에서 `interest_id = any (?)` array binding으로 실행되며, 이미 `uk_keywords_interest_keyword(interest_id, keyword)` 인덱스가 있고 조회 조건도 `interest_id` 기준이라 컬럼 순서와 맞다.
- `100k`에서는 데이터가 작아 planner가 `Seq Scan`을 선택했고, `1m`과 `10m`에서는 현재 적용된 `uk_keywords_interest_keyword` 인덱스를 사용했다.
- 따라서 keywords 쪽은 신규 인덱스 후보가 아니라 현재 인덱스 동작 확인 대상으로 본다.
- `users.deleted_at` 인덱스는 1차 후보로 두지 않고, `subscriptions.user_id` 개선 이후 subquery 비용이 남는 경우 추가 측정한다.

## 측정 SQL
### 구독 중인 관심사

`MID4-92`의 `SubscribeRepository.findSubscribedInterestActivities()` JPQL을 임시 Spring Boot test에서 실행하고 Hibernate SQL 로그로 캡처한 실제 main query다. 아래 SQL은 측정을 위해 `userId` binding만 literal로 치환했다.

```sql
SELECT
    s1_0.id,
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
    )
FROM subscriptions s1_0
JOIN interests i1_0 ON i1_0.id = s1_0.interest_id
JOIN users u1_0 ON u1_0.id = s1_0.user_id
WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001'
  AND u1_0.deleted_at IS NULL;
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
| `100k` | 구독 중인 관심사 main | `5.636 ms` | `5.251`, `5.769`, `5.337`, `5.025`, `5.056 ms` | `5.251 ms` |
| `100k` | 구독 관심사 keywords | `0.157 ms` | `1.244`, `1.140`, `1.165`, `1.170`, `1.725 ms` | `1.170 ms` |
| `100k` | 구독 중인 관심사 total | `-` | `5.564`, `6.572`, `6.450`, `6.243`, `6.131 ms` | `6.243 ms` |
| `1m` | 구독 중인 관심사 main | `4.122 ms` | `5.274`, `5.105`, `5.185`, `5.239`, `5.805 ms` | `5.239 ms` |
| `1m` | 구독 관심사 keywords | `0.101 ms` | `1.194`, `1.383`, `1.152`, `2.050`, `1.131 ms` | `1.194 ms` |
| `1m` | 구독 중인 관심사 total | `-` | `5.810`, `6.081`, `6.744`, `6.056`, `6.493 ms` | `6.081 ms` |
| `10m` | 구독 중인 관심사 main | `10.092 ms` | `11.915`, `13.347`, `12.133`, `13.852`, `11.992 ms` | `12.133 ms` |
| `10m` | 구독 관심사 keywords | `0.098 ms` | `1.169`, `1.167`, `1.162`, `1.124`, `1.356 ms` | `1.167 ms` |
| `10m` | 구독 중인 관심사 total | `-` | `14.648`, `12.402`, `12.632`, `13.230`, `14.459 ms` | `13.230 ms` |

## EXPLAIN 실행계획 요약

### 100k

구독 중인 관심사:

- main query의 `subscriptions`는 `user_id` 조건을 `Seq Scan`으로 처리했다.
- `Rows Removed by Filter`: `4995`
- 관심사별 구독자 수 subquery는 `uk_subscriptions_interest_user` 인덱스를 `interest_id = ...` 조건으로 사용했다.
- subquery 내부의 활성 사용자 필터는 `users`를 50회 `Seq Scan`했다.
- keywords query는 `interest_id = any (?)` 조건으로 실행됐고, `keywords` 1500건을 `Seq Scan`했다. 작은 scale에서는 planner가 `uk_keywords_interest_keyword`보다 sequential scan을 선택했다.

### 1m

구독 중인 관심사:

- main query의 `subscriptions`는 `user_id` 조건을 `Seq Scan`으로 처리했다.
- `Rows Removed by Filter`: `49995`
- 관심사별 구독자 수 subquery는 `uk_subscriptions_interest_user` 인덱스를 `interest_id = ...` 조건으로 사용했다.
- subquery 내부 사용자 확인은 `pk_users` 인덱스를 사용했다.
- keywords query는 `interest_id = any (?)` 조건으로 `uk_keywords_interest_keyword` 인덱스를 사용했다.

### 10m

구독 중인 관심사:

- main query의 `subscriptions`는 `user_id` 조건을 `Parallel Seq Scan`으로 처리했다.
- worker당 `Rows Removed by Filter`: `166665`, `loops=3`
- 관심사별 구독자 수 subquery는 `uk_subscriptions_interest_user` 인덱스를 `interest_id = ...` 조건으로 사용했다.
- keywords query는 `interest_id = any (?)` 조건으로 `uk_keywords_interest_keyword` 인덱스를 사용했다.
- 요청 1건 기준 total median은 `13.230 ms`로, 현재 scale에서는 최근 조회 기사보다 훨씬 작지만 `subscriptions.user_id` 접근 경로가 없어 병목 후보로 남는다.

## EXPLAIN 실행계획 원문

<details>
<summary>100k - 구독 중인 관심사</summary>

main query:

```text
Nested Loop  (cost=111.96..3529.46 rows=50 width=68) (actual time=0.454..5.554 rows=50 loops=1)
  Buffers: shared hit=1602
  ->  Index Scan using pk_users on users u1_0  (cost=0.28..8.29 rows=1 width=16) (actual time=0.006..0.006 rows=1 loops=1)
        Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
        Filter: (deleted_at IS NULL)
        Buffers: shared hit=3
  ->  Hash Join  (cost=111.69..123.01 rows=50 width=76) (actual time=0.255..0.313 rows=50 loops=1)
        Hash Cond: (i1_0.id = s1_0.interest_id)
        Buffers: shared hit=53
        ->  Seq Scan on interests i1_0  (cost=0.00..10.00 rows=500 width=44) (actual time=0.002..0.029 rows=500 loops=1)
              Buffers: shared hit=5
        ->  Hash  (cost=111.06..111.06 rows=50 width=48) (actual time=0.243..0.244 rows=50 loops=1)
              Buckets: 1024  Batches: 1  Memory Usage: 12kB
              Buffers: shared hit=48
              ->  Seq Scan on subscriptions s1_0  (cost=0.00..111.06 rows=50 width=48) (actual time=0.002..0.237 rows=50 loops=1)
                    Filter: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Rows Removed by Filter: 4995
                    Buffers: shared hit=48
  SubPlan 1
    ->  Aggregate  (cost=67.94..67.95 rows=1 width=8) (actual time=0.104..0.104 rows=1 loops=50)
          Buffers: shared hit=1546
          ->  Nested Loop  (cost=31.19..67.92 rows=10 width=16) (actual time=0.008..0.103 rows=11 loops=50)
                Buffers: shared hit=1546
                ->  Index Only Scan using pk_interests on interests i2_0  (cost=0.27..8.29 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=50)
                      Index Cond: (id = i1_0.id)
                      Heap Fetches: 50
                      Buffers: shared hit=150
                ->  Hash Join  (cost=30.92..59.53 rows=10 width=32) (actual time=0.007..0.101 rows=11 loops=50)
                      Hash Cond: (u2_0.id = s2_0.user_id)
                      Buffers: shared hit=1396
                      ->  Seq Scan on users u2_0  (cost=0.00..26.00 rows=990 width=16) (actual time=0.001..0.054 rows=990 loops=50)
                            Filter: (deleted_at IS NULL)
                            Rows Removed by Filter: 10
                            Buffers: shared hit=800
                      ->  Hash  (cost=30.79..30.79 rows=10 width=48) (actual time=0.006..0.006 rows=11 loops=50)
                            Buckets: 1024  Batches: 1  Memory Usage: 9kB
                            Buffers: shared hit=596
                            ->  Bitmap Heap Scan on subscriptions s2_0  (cost=4.36..30.79 rows=10 width=48) (actual time=0.002..0.005 rows=11 loops=50)
                                  Recheck Cond: (interest_id = i1_0.id)
                                  Heap Blocks: exact=496
                                  Buffers: shared hit=596
                                  ->  Bitmap Index Scan on uk_subscriptions_interest_user  (cost=0.00..4.36 rows=10 width=0) (actual time=0.001..0.001 rows=11 loops=50)
                                        Index Cond: (interest_id = i1_0.id)
                                        Buffers: shared hit=100
Planning:
  Buffers: shared hit=330
Planning Time: 0.790 ms
Execution Time: 5.636 ms
```

keywords query:

```text
Seq Scan on keywords k1_0  (cost=0.12..37.62 rows=150 width=45) (actual time=0.012..0.130 rows=150 loops=1)
  Filter: (interest_id = ANY ('{00000002-0000-4000-8000-000000000001,...,00000002-0000-4000-8000-000000000032}'::uuid[]))
  Rows Removed by Filter: 1350
  Buffers: shared hit=15
Planning:
  Buffers: shared hit=149
Planning Time: 0.541 ms
Execution Time: 0.157 ms
```

</details>

<details>
<summary>1m - 구독 중인 관심사</summary>

main query:

```text
Nested Loop  (cost=1094.66..9513.56 rows=65 width=69) (actual time=2.852..4.013 rows=50 loops=1)
  Buffers: shared hit=2949
  ->  Index Scan using pk_users on users u1_0  (cost=0.29..8.30 rows=1 width=16) (actual time=0.008..0.009 rows=1 loops=1)
        Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
        Filter: (deleted_at IS NULL)
        Buffers: shared hit=3
  ->  Hash Join  (cost=1094.38..1204.51 rows=65 width=77) (actual time=2.774..3.220 rows=50 loops=1)
        Hash Cond: (i1_0.id = s1_0.interest_id)
        Buffers: shared hit=515
        ->  Seq Scan on interests i1_0  (cost=0.00..97.00 rows=5000 width=45) (actual time=0.004..0.239 rows=5000 loops=1)
              Buffers: shared hit=47
        ->  Hash  (cost=1093.56..1093.56 rows=65 width=48) (actual time=2.756..2.757 rows=50 loops=1)
              Buckets: 1024  Batches: 1  Memory Usage: 12kB
              Buffers: shared hit=468
              ->  Seq Scan on subscriptions s1_0  (cost=0.00..1093.56 rows=65 width=48) (actual time=0.003..2.749 rows=50 loops=1)
                    Filter: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Rows Removed by Filter: 49995
                    Buffers: shared hit=468
  SubPlan 1
    ->  Aggregate  (cost=127.68..127.69 rows=1 width=8) (actual time=0.015..0.015 rows=1 loops=50)
          Buffers: shared hit=2431
          ->  Nested Loop  (cost=5.06..127.66 rows=10 width=16) (actual time=0.005..0.015 rows=11 loops=50)
                Buffers: shared hit=2431
                ->  Index Only Scan using pk_interests on interests i2_0  (cost=0.28..8.30 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=50)
                      Index Cond: (id = i1_0.id)
                      Heap Fetches: 50
                      Buffers: shared hit=150
                ->  Nested Loop  (cost=4.78..119.26 rows=10 width=32) (actual time=0.004..0.013 rows=11 loops=50)
                      Buffers: shared hit=2281
                      ->  Bitmap Heap Scan on subscriptions s2_0  (cost=4.49..40.23 rows=10 width=48) (actual time=0.003..0.005 rows=11 loops=50)
                            Recheck Cond: (interest_id = i1_0.id)
                            Heap Blocks: exact=496
                            Buffers: shared hit=646
                            ->  Bitmap Index Scan on uk_subscriptions_interest_user  (cost=0.00..4.49 rows=10 width=0) (actual time=0.002..0.002 rows=11 loops=50)
                                  Index Cond: (interest_id = i1_0.id)
                                  Buffers: shared hit=150
                      ->  Index Scan using pk_users on users u2_0  (cost=0.29..7.90 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=545)
                            Index Cond: (id = s2_0.user_id)
                            Filter: (deleted_at IS NULL)
                            Rows Removed by Filter: 0
                            Buffers: shared hit=1635
Planning:
  Buffers: shared hit=332
Planning Time: 0.764 ms
Execution Time: 4.122 ms
```

keywords query:

```text
Bitmap Heap Scan on keywords k1_0  (cost=183.54..332.68 rows=150 width=46) (actual time=0.044..0.065 rows=150 loops=1)
  Recheck Cond: (interest_id = ANY ('{00000002-0000-4000-8000-000000000001,...,00000002-0000-4000-8000-000000000032}'::uuid[]))
  Heap Blocks: exact=4
  Buffers: shared hit=104
  ->  Bitmap Index Scan on uk_keywords_interest_keyword  (cost=0.00..183.38 rows=150 width=0) (actual time=0.036..0.036 rows=150 loops=1)
        Index Cond: (interest_id = ANY ('{00000002-0000-4000-8000-000000000001,...,00000002-0000-4000-8000-000000000032}'::uuid[]))
        Buffers: shared hit=100
Planning:
  Buffers: shared hit=149
Planning Time: 0.395 ms
Execution Time: 0.101 ms
```

</details>

<details>
<summary>10m - 구독 중인 관심사</summary>

main query:

```text
Nested Loop  (cost=1000.71..8989.91 rows=5 width=70) (actual time=8.210..9.997 rows=50 loops=1)
  Buffers: shared hit=3570 read=4282
  ->  Index Scan using pk_users on users u1_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.007..0.008 rows=1 loops=1)
        Index Cond: (id = '00000001-0000-4000-8000-000000000001'::uuid)
        Filter: (deleted_at IS NULL)
        Buffers: shared hit=4
  ->  Gather  (cost=1000.29..8295.52 rows=5 width=78) (actual time=8.129..9.243 rows=50 loops=1)
        Workers Planned: 2
        Workers Launched: 2
        Buffers: shared hit=542 read=4282
        ->  Nested Loop  (cost=0.29..7295.02 rows=2 width=78) (actual time=6.588..6.605 rows=17 loops=3)
              Buffers: shared hit=542 read=4282
              ->  Parallel Seq Scan on subscriptions s1_0  (cost=0.00..7278.40 rows=2 width=48) (actual time=6.580..6.582 rows=17 loops=3)
                    Filter: (user_id = '00000001-0000-4000-8000-000000000001'::uuid)
                    Rows Removed by Filter: 166665
                    Buffers: shared hit=392 read=4282
              ->  Index Scan using pk_interests on interests i1_0  (cost=0.29..8.31 rows=1 width=46) (actual time=0.001..0.001 rows=1 loops=50)
                    Index Cond: (id = s1_0.interest_id)
                    Buffers: shared hit=150
  SubPlan 1
    ->  Aggregate  (cost=137.17..137.18 rows=1 width=8) (actual time=0.014..0.015 rows=1 loops=50)
          Buffers: shared hit=3024
          ->  Nested Loop  (cost=1.13..137.15 rows=10 width=16) (actual time=0.003..0.014 rows=11 loops=50)
                Buffers: shared hit=3024
                ->  Index Only Scan using pk_interests on interests i2_0  (cost=0.29..8.31 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=50)
                      Index Cond: (id = i1_0.id)
                      Heap Fetches: 50
                      Buffers: shared hit=150
                ->  Nested Loop  (cost=0.84..128.74 rows=10 width=32) (actual time=0.002..0.012 rows=11 loops=50)
                      Buffers: shared hit=2874
                      ->  Index Scan using uk_subscriptions_interest_user on subscriptions s2_0  (cost=0.42..44.39 rows=10 width=48) (actual time=0.001..0.004 rows=11 loops=50)
                            Index Cond: (interest_id = i1_0.id)
                            Buffers: shared hit=694
                      ->  Index Scan using pk_users on users u2_0  (cost=0.42..8.44 rows=1 width=16) (actual time=0.001..0.001 rows=1 loops=545)
                            Index Cond: (id = s2_0.user_id)
                            Filter: (deleted_at IS NULL)
                            Rows Removed by Filter: 0
                            Buffers: shared hit=2180
Planning:
  Buffers: shared hit=334
Planning Time: 0.788 ms
Execution Time: 10.092 ms
```

keywords query:

```text
Bitmap Heap Scan on keywords k1_0  (cost=218.29..655.53 rows=150 width=47) (actual time=0.046..0.063 rows=150 loops=1)
  Recheck Cond: (interest_id = ANY ('{00000002-0000-4000-8000-000000000001,...,00000002-0000-4000-8000-000000000032}'::uuid[]))
  Heap Blocks: exact=4
  Buffers: shared hit=154
  ->  Bitmap Index Scan on uk_keywords_interest_keyword  (cost=0.00..218.12 rows=150 width=0) (actual time=0.040..0.040 rows=150 loops=1)
        Index Cond: (interest_id = ANY ('{00000002-0000-4000-8000-000000000001,...,00000002-0000-4000-8000-000000000032}'::uuid[]))
        Buffers: shared hit=150
Planning:
  Buffers: shared hit=149
Planning Time: 0.359 ms
Execution Time: 0.098 ms
```

</details>
