# 단일 사용자 활동내역 API baseline

## 측정 대상

- API: `GET /api/user-activities/{userId}`
- userId: `00000001-0000-4000-8000-000000000001`
- app source: `origin/develop` `ffed6e1` 기준 MID4-132 브랜치
- k6 source: PR #69 `test/MID4-131-k6-baseline-script` `5a5d761`
- k6 실행 방식: PR #69 스크립트를 stdin으로 주입, MID4-132 PR diff에는 k6 파일을 포함하지 않음
- DB: `monew-perf-132-postgres-1`, PostgreSQL 16.14, port `5433 -> 5432`
- seed: `SEED_SCALE=100k`, 소요 `3.497 s`

## Smoke 결과

| metric | value |
| --- | ---: |
| requests | `1` |
| RPS | `1.73` |
| dropped iterations | `0` |
| error rate | `0.00%` |
| duration avg | `573.67 ms` |
| duration p95 | `573.67 ms` |
| duration p99 | `573.67 ms` |
| checks rate | `100.00%` |

smoke는 서버 기동 직후 단일 요청 검증이며, baseline 판단값으로 사용하지 않는다.

## Baseline 결과

실행 조건:

| setting | value |
| --- | --- |
| scenario | `baseline` |
| rate | `20` |
| timeUnit | `1s` |
| duration | `1m` |
| preAllocatedVUs | `20` |
| maxVUs | `100` |
| summary path | `/results/activity-history-100k-baseline-summary.json` |

결과:

| metric | value |
| --- | ---: |
| requests | `1201` |
| RPS | `20.01` |
| dropped iterations | `0` |
| error rate | `0.00%` |
| duration avg | `46.20 ms` |
| duration p95 | `53.58 ms` |
| duration p99 | `63.40 ms` |
| checks rate | `100.00%` |

summary JSON:

```json
{
  "scenario": "baseline",
  "url": "http://host.docker.internal:8080/api/user-activities/00000001-0000-4000-8000-000000000001",
  "targetUserId": "00000001-0000-4000-8000-000000000001",
  "expectedStatus": 200,
  "metrics": {
    "requests": 1201,
    "rps": 20.010966232098443,
    "droppedIterations": 0,
    "errorRate": 0,
    "durationAvgMs": 46.19865206661124,
    "durationP95Ms": 53.57953,
    "durationP99Ms": 63.400233,
    "checksRate": 1
  }
}
```

## DB 부하

baseline 직전 `pg_stat_reset()`을 실행해 통계를 분리했다.

중간 Docker stats:

```text
CPU 66.67%
MEM 78.62MiB / 30.91GiB
NET I/O 2.99MB / 17.7MB
BLOCK I/O 52.7MB / 188MB
PIDS 16
```

중간 `pg_stat_activity`:

| state | wait_event_type | count |
| --- | --- | ---: |
| active |  | `1` |
| idle | `Client` | `10` |

baseline 직후 `pg_stat_database`:

| metric | value |
| --- | ---: |
| xact_commit | `6040` |
| xact_rollback | `0` |
| blks_read | `4` |
| blks_hit | `25,672,054` |
| cache_hit_pct | `100.00` |
| tup_returned | `699,736,085` |
| tup_fetched | `5,636,161` |
| tup_inserted | `0` |
| tup_updated | `0` |
| tup_deleted | `0` |
| temp_files | `0` |
| temp_bytes | `0` |
| deadlocks | `0` |

baseline 직후 Docker stats:

```text
CPU 0.17%
MEM 79.13MiB / 30.91GiB
NET I/O 4.52MB / 26.9MB
BLOCK I/O 52.7MB / 206MB
PIDS 16
```

## 요청 1건당 SQL

코드 경로 기준 요청 1건은 6개 SQL로 구성된다. `UserActivityServiceImpl`은 사용자 조회 후 최근 조회 기사, 최근 작성 댓글, 최근 좋아요 댓글, 구독 관심사 조회를 순서대로 조립한다. 구독 관심사는 DTO 변환 중 `Interest.getKeywords()` batch load가 한 번 추가된다.

| 순서 | SQL | 주요 비용 |
| ---: | --- | --- |
| 1 | `users` PK 조회 | 사용자 존재 및 `deleted_at` 확인 |
| 2 | 최근 조회 기사 | `article_views`, `articles`, `users` join, 댓글 수 subquery, 조회수 subquery |
| 3 | 최근 작성 댓글 | `comments`, `users`, `articles` join, 좋아요 수 subquery |
| 4 | 최근 좋아요 댓글 | `comment_likes`, `comments`, `users`, `articles` join, 좋아요 수 subquery |
| 5 | 구독 관심사 main | `subscriptions`, `interests`, `users` join, 관심사별 구독자 수 subquery |
| 6 | 구독 관심사 keywords | `keywords.interest_id = any (?)` batch 조회 |

Spring transaction 경계는 상위 활동내역 서비스 전체에 하나로 묶이지 않고, 하위 repository/service 호출별 read transaction으로 나뉜다. reset 이후 `xact_commit=6040`은 1201 baseline 요청과 smoke/통계 조회를 포함한 값이다.

## 병목 판단

100k API baseline은 20 req/s에서 dropped iteration 없이 통과했다. 이 수치만 보면 현재 RDB 직접 조회는 1차 baseline으로 수용 가능하다.

다만 SQL 단위 10m baseline에서는 최근 조회 기사가 `1825.932 ms` median으로 가장 크다. 병목 후보는 `article_views.user_id` 접근 경로 부재와 댓글 수 subquery의 `comments.article_id` 반복 scan이다. MongoDB Read Model 적용 판단 전 MID4-133에서 RDB 인덱스 후보를 먼저 검증해야 한다.
