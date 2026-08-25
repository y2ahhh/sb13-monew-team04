# MID4-134 RDB optimized remeasure

## 요약

MID4-132 RDB baseline 위에 MID4-133 인덱스를 적용한 상태에서 활동내역 조회를 재측정했다. 이 문서는 원자료와 수치 비교만 기록한다. 병목 해석, 남는 후보 판단, MID4-125 연결 문구는 사용자 해석 후 보정한다.

## 측정 기준

- 대표 Jira: `MID4-134`
- 선행 stack: `MID4-132 -> MID4-133 -> MID4-134`
- 측정 branch: `docs/MID4-134-rdb-optimized-remeasure`
- 측정 당시 base: `fix/MID4-133-activity-history-indexes` (`ada8313`)
- API: `GET /api/user-activities/{userId}`
- 대상 사용자 ID: `00000001-0000-4000-8000-000000000001`
- DB project: `monew-perf-134`
- PostgreSQL container: `monew-perf-134-postgres-1`
- PostgreSQL port: `15434 -> 5432`
- App port: `8080`
- k6 조건: `20 rps`, `1m`, `preAllocatedVUs=20`, `maxVUs=100`
- 원자료: [raw](raw)

## 적용 인덱스 확인

원문: [raw/applied-indexes.txt](raw/applied-indexes.txt)

| table | index |
| --- | --- |
| `comments` | `idx_comments_user_created_id` |
| `comments` | `idx_comments_article` |
| `comment_likes` | `idx_comment_likes_liked_by_created_id` |
| `article_views` | `idx_article_views_user_viewed_id` |
| `subscriptions` | `idx_subscriptions_user_created_id` |

## API 결과 요약

상세: [api-remeasure.md](api-remeasure.md)

| seed scale | baseline p95 | optimized p95 | baseline p99 | optimized p99 | baseline RPS | optimized RPS | baseline dropped | optimized dropped |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `100k` | `53.58 ms` | `32.39 ms` | `63.40 ms` | `38.22 ms` | `20.01` | `20.82` | `0` | `0` |
| `1m` | `474.02 ms` | `26.25 ms` | `657.60 ms` | `33.43 ms` | `19.97` | `20.83` | `0` | `0` |
| `10m` | `32353.24 ms` | `30.00 ms` | `43167.63 ms` | `32.23 ms` | `3.11` | `20.83` | `979` | `0` |

## SQL median 요약

| 조회 | seed scale | baseline median | optimized median | delta | change |
| --- | --- | ---: | ---: | ---: | ---: |
| 최근 작성 댓글 | `100k` | `8.983 ms` | `0.529 ms` | `-8.454 ms` | `-94.11%` |
| 최근 작성 댓글 | `1m` | `14.005 ms` | `0.437 ms` | `-13.568 ms` | `-96.88%` |
| 최근 작성 댓글 | `10m` | `82.747 ms` | `0.594 ms` | `-82.153 ms` | `-99.28%` |
| 최근 좋아요한 댓글 | `100k` | `9.524 ms` | `0.809 ms` | `-8.715 ms` | `-91.51%` |
| 최근 좋아요한 댓글 | `1m` | `11.807 ms` | `1.025 ms` | `-10.782 ms` | `-91.32%` |
| 최근 좋아요한 댓글 | `10m` | `45.905 ms` | `0.836 ms` | `-45.069 ms` | `-98.18%` |
| 최근 조회 기사 | `100k` | `27.095 ms` | `0.659 ms` | `-26.436 ms` | `-97.57%` |
| 최근 조회 기사 | `1m` | `231.003 ms` | `0.519 ms` | `-230.484 ms` | `-99.78%` |
| 최근 조회 기사 | `10m` | `1825.932 ms` | `0.550 ms` | `-1825.382 ms` | `-99.97%` |
| 구독 중인 관심사 total | `100k` | `4.472 ms` | `4.566 ms` | `0.094 ms` | `2.10%` |
| 구독 중인 관심사 total | `1m` | `3.549 ms` | `1.470 ms` | `-2.079 ms` | `-58.58%` |
| 구독 중인 관심사 total | `10m` | `11.635 ms` | `1.329 ms` | `-10.306 ms` | `-88.58%` |

## 상세 문서

- [api-remeasure.md](api-remeasure.md)
- [recent-comments.md](recent-comments.md)
- [recent-liked-comments.md](recent-liked-comments.md)
- [recent-article-views.md](recent-article-views.md)
- [subscribed-interests.md](subscribed-interests.md)

## 해석 보류 항목

- 최적화 후에도 남는 병목 후보: 사용자 해석 예정
- MID4-125 MongoDB Read Model 판단 연결: 사용자 해석 예정
- 문장 보정: 사용자 해석 작성 후 수치 기준으로 검산
