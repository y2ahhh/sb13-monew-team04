# 단일 사용자 활동내역 API 재측정

## 측정 대상

- API: `GET /api/user-activities/{userId}`
- userId: `00000001-0000-4000-8000-000000000001`
- k6 source: `scripts/performance/activity-history/k6/activity-history-baseline.js`
- summary JSON:
  - [raw/activity-history-100k-optimized-summary.json](raw/activity-history-100k-optimized-summary.json)
  - [raw/activity-history-1m-optimized-summary.json](raw/activity-history-1m-optimized-summary.json)
  - [raw/activity-history-10m-optimized-summary.json](raw/activity-history-10m-optimized-summary.json)

## Seed 결과

| seed scale | seed duration | users | interests | subscriptions | articles | comments | comment_likes | article_views |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `100k` | `3.793 s` | `1,000` | `500` | `5,045` | `20,000` | `40,000` | `30,000` | `30,000` |
| `1m` | `17.043 s` | `10,000` | `5,000` | `50,045` | `200,000` | `400,000` | `300,000` | `300,000` |
| `10m` | `150.175 s` | `100,000` | `50,000` | `500,045` | `2,000,000` | `4,000,000` | `3,000,000` | `3,000,000` |

## k6 조건

| setting | value |
| --- | --- |
| scenario | `baseline` |
| rate | `20` |
| timeUnit | `1s` |
| duration | `1m` |
| preAllocatedVUs | `20` |
| maxVUs | `100` |

## Optimized 결과

| seed scale | summary path | requests | RPS | dropped iterations | error rate | duration avg | duration p95 | duration p99 | checks rate |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `100k` | `/results/activity-history-100k-optimized-summary.json` | `1200` | `20.82` | `0` | `0.00%` | `24.23 ms` | `32.39 ms` | `38.22 ms` | `100.00%` |
| `1m` | `/results/activity-history-1m-optimized-summary.json` | `1201` | `20.83` | `0` | `0.00%` | `19.84 ms` | `26.25 ms` | `33.43 ms` | `100.00%` |
| `10m` | `/results/activity-history-10m-optimized-summary.json` | `1200` | `20.83` | `0` | `0.00%` | `20.74 ms` | `30.00 ms` | `32.23 ms` | `100.00%` |

## Baseline 대비 p95/p99

| seed scale | p95 baseline | p95 optimized | p95 delta | p95 change | p99 baseline | p99 optimized | p99 delta | p99 change |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `100k` | `53.58 ms` | `32.39 ms` | `-21.187 ms` | `-39.54%` | `63.40 ms` | `38.22 ms` | `-25.179 ms` | `-39.71%` |
| `1m` | `474.02 ms` | `26.25 ms` | `-447.774 ms` | `-94.46%` | `657.60 ms` | `33.43 ms` | `-624.166 ms` | `-94.92%` |
| `10m` | `32353.24 ms` | `30.00 ms` | `-32323.246 ms` | `-99.91%` | `43167.63 ms` | `32.23 ms` | `-43135.401 ms` | `-99.93%` |

## DB 부하 원자료 요약

Docker stats mid-run 원문:

- [raw/docker-stats-mid-100k.txt](raw/docker-stats-mid-100k.txt)
- [raw/docker-stats-mid-1m.txt](raw/docker-stats-mid-1m.txt)
- [raw/docker-stats-mid-10m.txt](raw/docker-stats-mid-10m.txt)

| seed scale | CPU | MEM | NET I/O | BLOCK I/O | PIDS |
| --- | ---: | ---: | ---: | ---: | ---: |
| `100k` | `11.75%` | `88.21MiB / 30.91GiB` | `2.38MB / 14.3MB` | `24.9MB / 227MB` | `16` |
| `1m` | `9.90%` | `284.3MiB / 30.91GiB` | `6.66MB / 41MB` | `29.4MB / 2.11GB` | `16` |
| `10m` | `10.05%` | `2.32GiB / 30.91GiB` | `11MB / 67.8MB` | `106MB / 18.5GB` | `16` |

`pg_stat_database` 원문:

- [raw/pg-stat-database-after-100k.txt](raw/pg-stat-database-after-100k.txt)
- [raw/pg-stat-database-after-1m.txt](raw/pg-stat-database-after-1m.txt)
- [raw/pg-stat-database-after-10m.txt](raw/pg-stat-database-after-10m.txt)

| seed scale | xact_commit | blks_read | blks_hit | cache_hit_pct | tup_returned | tup_fetched | temp_files | temp_bytes | deadlocks |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `100k` | `5,934` | `4` | `2,627,252` | `100.00` | `62,634,188` | `1,117,218` | `0` | `0` | `0` |
| `1m` | `5,992` | `58` | `4,125,654` | `100.00` | `2,005,382` | `2,004,412` | `0` | `0` | `0` |
| `10m` | `5,997` | `66,299` | `5,027,425` | `98.70` | `1,976,304` | `1,970,019` | `0` | `0` | `0` |

## 요청당 SQL 수

MID4-132와 같은 코드 경로 기준으로 요청 1건은 6개 SQL로 본다. 이번 작업에서는 JPQL, fetch 전략, 서비스 조립 로직을 변경하지 않았다.

| 순서 | 조회 |
| ---: | --- |
| 1 | 사용자 조회 |
| 2 | 최근 조회 기사 |
| 3 | 최근 작성 댓글 |
| 4 | 최근 좋아요 댓글 |
| 5 | 구독 관심사 main |
| 6 | 구독 관심사 keywords |

## 해석 보류

최적화 후 병목 후보와 MID4-125 연결 판단은 사용자 해석 예정.
