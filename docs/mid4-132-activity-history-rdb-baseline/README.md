# MID4-132 활동내역 RDB baseline 측정

## 요약

활동내역 RDB 조회 작업의 인덱스 적용 전 baseline과 후속 인덱스 후보를 정리한다. 이 문서는 전체 요약 진입점이며, 조회별 SQL과 EXPLAIN 원문은 상세 문서로 분리한다.

## 상세 문서

| 조회 | 상세 문서 | 10m median | 1차 후보 |
| --- | --- | ---: | --- |
| 최근 작성 댓글 | [recent-comments.md](recent-comments.md) | `82.747 ms` | `comments(user_id, created_at DESC, id DESC)` |
| 최근 좋아요한 댓글 | [recent-liked-comments.md](recent-liked-comments.md) | `45.905 ms` | `comment_likes(liked_by, created_at DESC, id DESC)` |
| 최근 조회 기사 | [recent-article-views.md](recent-article-views.md) | `1825.932 ms` | `article_views(user_id, viewed_at DESC, id DESC)`, `comments(article_id)` |
| 구독 중인 관심사 | [subscribed-interests.md](subscribed-interests.md) | `13.230 ms` | 현재: `subscriptions(user_id)`, `subscriptions(user_id, interest_id)` / 후속 정렬: `subscriptions(user_id, created_at DESC, id DESC)` |

## 인덱스 후보 요약

| 조회 | 병목 | 후보 | 비고 |
| --- | --- | --- | --- |
| 최근 작성 댓글 | `comments.user_id` 접근 경로 부재, 최신순 정렬 | `comments(user_id, created_at DESC, id DESC)` | `deleted_at IS NULL`은 1차 후보에서 제외 |
| 최근 좋아요한 댓글 | `comment_likes.liked_by` 접근 경로 부재, 최신순 정렬 | `comment_likes(liked_by, created_at DESC, id DESC)` | 기존 `(comment_id, liked_by)` unique index는 선두 컬럼이 맞지 않음 |
| 최근 조회 기사 | `article_views.user_id` 접근 경로 부재, 최신순 정렬 | `article_views(user_id, viewed_at DESC, id DESC)` | 기존 article_id 선두 인덱스들은 main query에 부적합 |
| 최근 조회 기사 댓글 수 | `comments.article_id` 접근 경로 부재 | `comments(article_id)` | 댓글 수 subquery의 반복 full scan 제거 후보 |
| 구독 중인 관심사 | 현재: `subscriptions.user_id` 접근 경로 부재 / 후속: 구독 생성일자 최신순 정렬 | 현재: `subscriptions(user_id)`, `subscriptions(user_id, interest_id)` / 후속: `subscriptions(user_id, created_at DESC, id DESC)` | 최종 후보 확정 후 중복/불필요 인덱스 제거 검토 |

## 측정 기준

- 초기 baseline seed/table snapshot 시각: 2026-08-23 08:07:24 +09:00
- 최근 활동 3종 actual SQL 측정 시각: 2026-08-23 10:23:40 +09:00
- 구독 중인 관심사 actual SQL 측정 시각: 2026-08-23 10:07:00 +09:00
- 문서 브랜치: `docs/MID4-132-activity-history-rdb-baseline`
- 기준 커밋: `f7b198a`
- 대표 Jira: `MID4-132`
- Parent: `MID4-77`
- 관련 Jira: `MID4-92` 구독 중인 관심사 조회, `MID4-130` 시드 데이터
- 후속 Jira: `MID4-133` 인덱스 후보 판별 및 최적화
- 측정 DB project: `monew-perf-rerun`
- 대상 사용자 ID: `00000001-0000-4000-8000-000000000001`
- PostgreSQL container: `monew-perf-rerun-postgres-1`
- Docker image: `postgres:16`
- PostgreSQL version: `PostgreSQL 16.14 (Debian 16.14-1.pgdg13+1)`
- Port: `5435 -> 5432`
- Docker engine: `linux x86_64`, CPU `24`, memory `33185484800 bytes` 약 `30.9 GiB`

측정 실행 세트:

| 측정 세트 | 대상 | SQL 출처 | 측정 시각 | seed 소요 시간 |
| --- | --- | --- | --- | --- |
| 초기 baseline snapshot | Seed 결과, 테이블 크기 | DB catalog 조회 | 2026-08-23 08:07:24 +09:00 | `100k` `3.139 s`, `1m` `14.155 s`, `10m` `124.082 s` |
| 최근 활동 3종 actual SQL | 최근 작성 댓글, 최근 좋아요한 댓글, 최근 조회 기사 | Hibernate SQL 로그 | 2026-08-23 10:23:40 +09:00 | `100k` `3.214 s`, `1m` `14.048 s`, `10m` `124.057 s` |
| 구독 중인 관심사 actual SQL | 구독 중인 관심사 main, keywords | Hibernate SQL 로그 | 2026-08-23 10:07:00 +09:00 | `100k` `3.229 s`, `1m` `13.854 s`, `10m` `125.173 s` |

아래 Seed 결과 테이블은 초기 baseline snapshot 실행값이다. 실제 조회 SQL 측정은 각 query set마다 데이터를 다시 생성한 뒤 수행했으므로 seed 소요 시간이 snapshot과 약간 다르다.

최근 활동 3종 조회는 Hibernate SQL 로그로 확인한 actual SQL 기준으로 측정했다.

구독 중인 관심사 조회도 Hibernate SQL 로그로 확인한 actual SQL 기준으로 측정했다. 구독 중인 관심사 측정은 최근 활동 3종 이후 별도로 추가했다.

PostgreSQL 주요 설정:

| 설정 | 값 |
| --- | ---: |
| `shared_buffers` | `16384 8kB` 약 `128 MB` |
| `work_mem` | `4096 kB` 약 `4 MB` |
| `effective_cache_size` | `524288 8kB` 약 `4 GB` |
| `max_connections` | `100` |

현재 DB 성능은 별도 `pgbench` TPS가 아니라, 위 로컬 Docker PostgreSQL 환경에서 활동내역 조회 SQL이 실제로 보인 latency와 실행계획을 기준으로 판단한다.

## Schema 확인

fresh DB에 적용된 Flyway migration:

```text
1 | 1 | init schema | true
2 | 202608180652 | add subscriptions unique constraint | true
3 | 202608180659 | add keywords unique constraint | true
4 | 202608181717 | add unique constraint users email | true
5 | 202608182356 | add article constraints and indexes | true
6 | 202608182357 | add comment likes unique constraint | true
7 | 202608190001 | drop article views user viewed index | true
8 | 202608190111 | add unique constraint interests name | true
```

측정 전 적용 인덱스:

아래 목록은 `pg_indexes`에서 직접 조회한 baseline 기준 인덱스다. `notifications`는 이번 측정 SQL에 직접 등장하지 않아 목록에 포함하지 않았다.

| Table | Index | Columns |
| --- | --- | --- |
| `comments` | `pk_comments` | `id` |
| `comment_likes` | `pk_comment_likes` | `id` |
| `comment_likes` | `uk_comment_likes_comment_liked_by` | `comment_id, liked_by` |
| `users` | `pk_users` | `id` |
| `users` | `uk_users_email` | `email` |
| `articles` | `pk_articles` | `id` |
| `articles` | `uk_articles_link` | `link` |
| `articles` | `idx_articles_source` | `source` |
| `articles` | `idx_articles_date` | `date DESC` |
| `articles` | `idx_articles_source_date` | `source, date DESC` |
| `article_views` | `pk_article_views` | `id` |
| `article_views` | `uk_article_views_article_user` | `article_id, user_id` |
| `article_views` | `idx_article_views_article_viewed` | `article_id, viewed_at DESC` |
| `interests` | `pk_interests` | `id` |
| `interests` | `uk_interests_name` | `name` |
| `subscriptions` | `pk_subscriptions` | `id` |
| `subscriptions` | `uk_subscriptions_interest_user` | `interest_id, user_id` |
| `keywords` | `pk_keywords` | `id` |
| `keywords` | `uk_keywords_interest_keyword` | `interest_id, keyword` |

PostgreSQL에서는 FK 선언만으로 referencing column 인덱스가 자동 생성되지 않는다. `V1__init_schema.sql`의 `fk_comment_likes_comment_id`는 참조 무결성만 보장한다. 다만 현재 schema에는 `(comment_id, liked_by)` unique constraint가 적용되어 있고, 이 제약이 생성한 unique index를 `comment_id = ...` 조건에서 사용할 수 있다.

구독도 같은 기준이다. `subscriptions`에는 `(interest_id, user_id)` unique index가 있으므로 `interest_id = ...` 조건에는 사용할 수 있지만, `user_id = ...` 단독 조건에는 선두 컬럼이 맞지 않아 바로 사용할 수 없다.

MySQL InnoDB는 PostgreSQL과 다르게 FK referencing column에 적절한 인덱스가 없으면 자동으로 생성한다. 이번 측정은 PostgreSQL 기준이다.

## 측정 방법

각 scale마다 아래 순서로 진행했다.

1. `postgres-seed`로 데이터를 처음부터 재생성한다.
2. seed 함수 끝에서 대상 테이블을 `ANALYZE`해 통계 정보를 갱신한다.
3. seed 소요 시간은 PowerShell `Measure-Command`로 측정한다.
4. 각 조회 SQL을 3회 warm-up 실행한다.
5. `EXPLAIN (ANALYZE, BUFFERS)`로 실행계획을 확인한다.
6. 같은 SQL을 5회 반복 실행하고 psql `\timing` 결과의 median을 대표값으로 기록한다.

대량 데이터를 넣은 뒤 통계 정보가 갱신되어야 PostgreSQL optimizer가 실제 데이터 분포에 가까운 실행계획을 선택할 수 있으므로, seed 함수에서 `users`, `interests`, `keywords`, `subscriptions`, `articles`, `comments`, `comment_likes`, `article_views`를 `ANALYZE`했다.

최근 작성 댓글, 최근 좋아요한 댓글, 최근 조회 기사 조회는 `test/MID4-77-activity-history-integration-check` 브랜치의 repository 메서드를 임시 Spring Boot test에서 실행한 뒤 Hibernate SQL 로그로 캡처한 actual SQL 기준으로 측정했다. 실제 SQL은 `LIMIT 10`이 아니라 `fetch first 10 rows only`로 렌더링된다.

`MID4-92` 구독 중인 관심사 조회는 아직 develop에 반영되지 않은 `feat/MID4-92-subscribed-interest-activity`의 `SubscribeRepository.findSubscribedInterestActivities()` JPQL을 임시 Spring Boot test에서 실행한 뒤 Hibernate SQL 로그로 캡처해 측정했다. API 조립 전 단계이므로 p95/p99, error rate가 아니라 SQL 단위 실행 시간과 실행계획만 기록한다.

Seed 실행 명령:

```powershell
$env:MONEW_DB_PORT='5435'
docker compose -p monew-perf-rerun --env-file .env.perf.local --profile perf-seed run --rm -e SEED_SCALE=100k postgres-seed
docker compose -p monew-perf-rerun --env-file .env.perf.local --profile perf-seed run --rm -e SEED_SCALE=1m postgres-seed
docker compose -p monew-perf-rerun --env-file .env.perf.local --profile perf-seed run --rm -e SEED_SCALE=10m postgres-seed
```

## Seed 결과

| Scale | Seed 소요 시간 | DB size | users | interests | keywords | subscriptions | articles | comments | comment_likes | article_views | target rows |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `100k` | `3.139 s` | `36 MB` | `1,000` | `500` | `1,500` | `5,045` | `20,000` | `40,000` | `30,000` | `30,000` | `1,000` |
| `1m` | `14.155 s` | `283 MB` | `10,000` | `5,000` | `15,000` | `50,045` | `200,000` | `400,000` | `300,000` | `300,000` | `10,000` |
| `10m` | `124.082 s` | `2774 MB` | `100,000` | `50,000` | `150,000` | `500,045` | `2,000,000` | `4,000,000` | `3,000,000` | `3,000,000` | `10,000` |

주요 테이블 크기:

| Scale | articles | comments | comment_likes | article_views | users | subscriptions |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `100k` | `8632 kB` | `5928 kB` | `5000 kB` | `7144 kB` | `304 kB` | `1016 kB` |
| `1m` | `85 MB` | `57 MB` | `48 MB` | `69 MB` | `2464 kB` | `9352 kB` |
| `10m` | `848 MB` | `595 MB` | `475 MB` | `700 MB` | `23 MB` | `91 MB` |

구독 관심사 조회 대상 데이터:

| Scale | target subscriptions | target keywords | DB size |
| --- | ---: | ---: | ---: |
| `100k` | `50` | `150` | `36 MB` |
| `1m` | `50` | `150` | `283 MB` |
| `10m` | `50` | `150` | `2774 MB` |

## 실행 시간 요약

| Scale | 조회 | EXPLAIN Execution Time | 반복 실행 시간 5회 | Median |
| --- | --- | ---: | --- | ---: |
| `100k` | 최근 작성 댓글 | `7.736 ms` | `8.953`, `8.983`, `11.553`, `9.165`, `8.712 ms` | `8.983 ms` |
| `100k` | 최근 좋아요한 댓글 | `8.936 ms` | `9.066`, `9.725`, `11.235`, `9.524`, `8.936 ms` | `9.524 ms` |
| `100k` | 최근 조회 기사 | `25.568 ms` | `25.981`, `27.132`, `27.095`, `27.150`, `26.654 ms` | `27.095 ms` |
| `100k` | 구독 중인 관심사 main | `5.636 ms` | `5.251`, `5.769`, `5.337`, `5.025`, `5.056 ms` | `5.251 ms` |
| `100k` | 구독 관심사 keywords | `0.157 ms` | `1.244`, `1.140`, `1.165`, `1.170`, `1.725 ms` | `1.170 ms` |
| `100k` | 구독 중인 관심사 total | `-` | `5.564`, `6.572`, `6.450`, `6.243`, `6.131 ms` | `6.243 ms` |
| `1m` | 최근 작성 댓글 | `12.836 ms` | `14.151`, `13.587`, `13.720`, `17.696`, `14.005 ms` | `14.005 ms` |
| `1m` | 최근 좋아요한 댓글 | `10.515 ms` | `12.238`, `11.750`, `12.216`, `11.807`, `11.632 ms` | `11.807 ms` |
| `1m` | 최근 조회 기사 | `235.410 ms` | `236.583`, `226.549`, `231.003`, `234.893`, `226.372 ms` | `231.003 ms` |
| `1m` | 구독 중인 관심사 main | `4.122 ms` | `5.274`, `5.105`, `5.185`, `5.239`, `5.805 ms` | `5.239 ms` |
| `1m` | 구독 관심사 keywords | `0.101 ms` | `1.194`, `1.383`, `1.152`, `2.050`, `1.131 ms` | `1.194 ms` |
| `1m` | 구독 중인 관심사 total | `-` | `5.810`, `6.081`, `6.744`, `6.056`, `6.493 ms` | `6.081 ms` |
| `10m` | 최근 작성 댓글 | `88.314 ms` | `80.545`, `82.747`, `95.963`, `93.401`, `78.940 ms` | `82.747 ms` |
| `10m` | 최근 좋아요한 댓글 | `44.102 ms` | `48.387`, `52.918`, `45.801`, `44.662`, `45.905 ms` | `45.905 ms` |
| `10m` | 최근 조회 기사 | `1820.813 ms` | `1825.932`, `1805.697`, `1861.183`, `1813.368`, `1832.137 ms` | `1825.932 ms` |
| `10m` | 구독 중인 관심사 main | `10.092 ms` | `11.915`, `13.347`, `12.133`, `13.852`, `11.992 ms` | `12.133 ms` |
| `10m` | 구독 관심사 keywords | `0.098 ms` | `1.169`, `1.167`, `1.162`, `1.124`, `1.356 ms` | `1.167 ms` |
| `10m` | 구독 중인 관심사 total | `-` | `14.648`, `12.402`, `12.632`, `13.230`, `14.459 ms` | `13.230 ms` |
