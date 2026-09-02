# MID4-244 RDB 실패 경계 후속 재측정

> 이전 작업: [MID4-227 노출 상태 적용 전후 재측정](../mid4-227-rdb-bottleneck-remeasure/README.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

## 문서 구성

| 문서 | 확인할 내용 |
| --- | --- |
| 현재 문서 | 핵심 SQL·API 결과와 기술 판단 |
| [API 실패 경계 상세 결과](api-boundary-results.md) | 회차별 부하 결과, 요청당 집계량과 해석의 한계 |
| [측정 조건과 재현 방법](measurement-and-reproduction.md) | 공통 조건, 데이터 준비, 재현 명령과 검증 결과 |
| [최근 작성 댓글](recent-comments.md) | 댓글별 활성 좋아요 수 실행계획 |
| [최근 좋아요한 댓글](recent-liked-comments.md) | 좋아요한 댓글과 활성 좋아요 수 실행계획 |
| [최근 조회 기사](recent-article-views.md) | 기사별 활성 댓글·조회 수와 `COUNT(*)` 비교 |
| [구독 관심사](subscribed-interests.md) | 관심사별 활성 구독자 수 실행계획 |

## 한눈에 보는 결론

- 좋아요·댓글·조회·구독 데이터가 특정 대상에 몰린 조건에서는 활성 데이터만 저장한 부분 커버링 인덱스 4개가 테이블 재방문과 버퍼 사용량을 줄여 `200 rps · 10분`을 통과했다.
- 같은 조건에서 `250 rps`는 3회 모두 통과했고 `275 rps`는 한 회차가 실패한 변동 구간, `300 rps`는 3회 모두 실패였다.
- 데이터 몰림 규모를 2배로 늘리자 요청당 집계 항목이 약 `17.1만 개`에서 `34.1만 개`로 늘었고, 후보 인덱스를 사용해도 `200 rps`의 첫 1분부터 실패했다.
- 일반 데이터는 현재 코드와 기존 인덱스로 `300 rps · 10분`을 통과했다. `350 rps`는 1분 측정을 통과했지만 10분 동안 유지하지 못했다.
- 기사 조회 수는 JPQL을 `COUNT(*)`로 바꾸면 `INCLUDE(id)` 없는 ACTIVE 부분 인덱스도 `Index Only Scan`으로 동작했다. 인덱스 크기는 `144 MB`에서 `76 MB`로 줄었다.
- 후보 인덱스, `COUNT(*)` 변경과 MongoDB 조회 모델은 코드와 Flyway에 반영하지 않았다. 운영 요구량과 쓰기 비용을 확인한 뒤 적용 범위를 결정한다.

## 왜 실패 조건만 측정했나

이미 통과한 조건을 같은 코드로 다시 실행해도 새로운 판단 근거가 늘지 않는다. 이번 작업은 아래 두 실패 기록에서 시작했다.

| 출발점 | 이전 결과 | 이번 확인 방식 |
| --- | --- | --- |
| MID4-227 데이터 몰림 `200 rps · 1분` | p95 `379.23 ms`로 실패 | 현재 코드에서 기존 인덱스와 후보 인덱스를 같은 데이터 몰림 조건으로 비교 |
| MID4-206 일반 `300 rps · 10분` | p95 `1,692.74 ms`, p99 `1,934.22 ms`, 요청 누락 `1,159건` | 현재 코드로 먼저 재현하고, 실패할 때만 후보 적용 |

MID4-227에서 통과한 `150 rps` 이하, 제외 데이터(exclusion), 읽기·쓰기 혼합(mixed) 조건과 MID4-206에서 통과한 `250 rps` 이하는 재측정 대상에서 제외했다. 구체적인 반복 횟수와 데이터 준비 순서는 [측정 조건과 재현 방법](measurement-and-reproduction.md)에서 확인할 수 있다.

## 비교한 인덱스 후보

활성 데이터만 인덱스에 넣어 크기를 줄이고, 현재 `count(id)`에 필요한 ID를 함께 저장해 실제 테이블을 다시 방문하는 비용을 줄이는 후보 인덱스다.

```sql
CREATE INDEX IF NOT EXISTS idx_perf_comments_active_article_cover
    ON comments(article_id) INCLUDE(id)
    WHERE visibility_status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_perf_article_views_active_article_cover
    ON article_views(article_id) INCLUDE(id)
    WHERE visibility_status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_perf_comment_likes_active_comment_cover
    ON comment_likes(comment_id) INCLUDE(id)
    WHERE visibility_status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_perf_subscriptions_active_interest_cover
    ON subscriptions(interest_id) INCLUDE(id)
    WHERE visibility_status = 'ACTIVE';
```

데이터 몰림 조건에서 측정한 후보 인덱스 크기는 `comments 177 MB`, `article_views 144 MB`, `comment_likes 132 MB`, `subscriptions 26 MB`로 합계 약 `479 MB`다.

## SQL 비교 결과

일반 실행 5회의 가운데 값이며 단위는 `ms`다. 개선율이 양수이면 후보가 빨라진 것이다.

### 데이터 몰림 조건

| SQL | 기존 인덱스 | 후보 인덱스 | 개선율 | 대표 실행계획의 공유 버퍼(기존 → 후보) |
| --- | ---: | ---: | ---: | ---: |
| [최근 작성 댓글](recent-comments.md) | 3.537 | 2.894 | 18.2% | 313 → 158 |
| [최근 좋아요한 댓글](recent-liked-comments.md) | 2.649 | 3.035 | -14.6% | 174 → 155 |
| [최근 조회 기사](recent-article-views.md) | 17.793 | 7.826 | 56.0% | 2,456 → 806 |
| [구독 관심사](subscribed-interests.md) | 9.319 | 4.693 | 49.6% | 1,944 → 614 |

### 데이터 몰림 2배 조건

| SQL | 기존 인덱스 | 후보 인덱스 | 개선율 | 대표 실행계획의 공유 버퍼(기존 → 후보) |
| --- | ---: | ---: | ---: | ---: |
| [최근 작성 댓글](recent-comments.md) | 4.516 | 3.644 | 19.3% | 537 → 218 |
| [최근 좋아요한 댓글](recent-liked-comments.md) | 2.337 | 2.568 | -9.9% | 174 → 155 |
| [최근 조회 기사](recent-article-views.md) | 34.868 | 15.035 | 56.9% | 7,863 → 1,474 |
| [구독 관심사](subscribed-interests.md) | 13.635 | 7.269 | 46.7% | 3,123 → 910 |

워밍업 뒤 대표 실행계획의 `shared read`는 모두 `0`이었다. 후보 인덱스는 대부분의 집계를 `Index Only Scan`으로 바꾸고 버퍼 사용량을 줄였지만, 결과별 하위 조회와 조건에 맞는 항목을 세는 작업은 유지됐다. 요청당 상세 집계량은 [API 실패 경계 상세 결과](api-boundary-results.md#요청당-집계량과-실패-원인)에 기록했다.

## API 실패 경계 요약

| 데이터 조건 | 인덱스 상태 | 확인 결과 | 판단 |
| --- | --- | --- | --- |
| 데이터 몰림, `200 rps` | 기존 | 1분 3회 중 1회 실패 | 안정 통과로 보기 어려움 |
| 데이터 몰림, `200 rps` | 후보 | 1분 3회와 10분 지속 통과 | 인덱스 효과 확인 |
| 데이터 몰림, 요청량 상향 | 후보 | `250 rps` 3회 통과, `275 rps` 2회 통과·1회 실패, `300 rps` 3회 실패 | 250 안정, 275 변동, 300 반복 실패 |
| 데이터 몰림 2배, `200 rps` | 후보 | p95 `2,094.53 ms`, p99 `2,185.07 ms` | 첫 1분부터 실패 |
| 일반 데이터 | 기존 | `300 rps · 10분` 통과, `350 rps · 1분` 3회 통과 | 단기와 지속 처리량이 다름 |
| 일반 데이터, 요청량 상향 | 기존 | `375 rps` 2회 통과·1회 실패, `400 rps` 1회 통과·2회 실패 | 375 변동, 400 반복 실패 |
| 일반 데이터, `350 rps` | 기존 | 10분 동안 요청 누락 `6,646건`, p95 `1,713.57 ms` | 지속 측정 실패 |

회차별 p95·p99, 요청 누락, 워밍업과 캐시 확인 근거는 [API 실패 경계 상세 결과](api-boundary-results.md)에서 확인할 수 있다.

## 판단과 다음 작업

후보 인덱스는 효과가 확인된 세 조회의 SQL 실행 시간을 약 `19~57%` 줄이고 실제 테이블을 다시 방문하는 비용도 낮췄지만, 반복 집계 자체를 제거하지는 않았다. 요청당 확인 항목이 데이터 몰림 조건 약 `17.1만 개`, 2배 조건 약 `34.1만 개`로 증가한 결과는 데이터가 한 대상에 더 몰리면 같은 병목이 다시 나타남을 보여준다.

현재 JPQL의 `COUNT(별칭)`은 Hibernate에서 `count(별칭.id)`로 변환되므로 `INCLUDE(id)`가 Index Only Scan에 필요하다. 후속 확인에서 `COUNT(*)`는 그대로 `count(*)`를 생성했고, 기사 조회 수 집계는 `INCLUDE(id)` 없는 ACTIVE 부분 인덱스만으로도 `Index Only Scan`, `Heap Fetches 0`을 달성했다. 활성 조회 `10,003건` 조건의 인덱스 크기는 `144 MB`에서 `76 MB`로 줄었다. 상세 2×2 비교는 [최근 조회 기사 실행계획](recent-article-views.md#count와-includeid-비교)에 기록했다.

일반 데이터의 현재 지속 통과 기준은 확인이 끝난 `300 rps · 10분`이다. `301~349 rps`의 10분 결과는 측정하지 않았으므로 정확한 최대 지속 RPS는 단정하지 않는다. 더 높은 지속 처리량이 요구되기 전까지 조회 구조를 미리 복잡하게 바꾸지 않는다.

개수 계산만 실제 병목이 되면 카운터 테이블을 먼저 검토한다. 활동 내역 조회 전체를 RDB와 분리해 독립적으로 확장해야 한다면 MongoDB 조회 모델을 비교한다. 두 방식 모두 실제 운영 요청량과 목표 응답 시간이 확인된 뒤 선택한다.

운영 인덱스로 확정하기 전에는 아래 항목을 추가로 확인해야 한다.

- 약 `479 MB`의 추가 저장 공간이 운영 DB에서 허용되는가
- 댓글·조회·좋아요·구독 쓰기 성능이 새 인덱스로 얼마나 느려지는가
- `COUNT(*)`로 집계를 바꾸고 더 작은 부분 인덱스를 사용할 수 있는 범위는 어디까지인가
- 네 인덱스를 모두 둘지, 효과가 큰 기사 조회와 구독 집계만 선택할지

이번 작업에서는 후보 인덱스, JPQL, Flyway, 카운터 테이블과 MongoDB 조회 모델을 구현하지 않았다. 측정 환경과 결과 해석의 제약은 [API 실패 경계 상세 결과의 한계](api-boundary-results.md#결과를-해석할-때의-한계)에서 확인할 수 있다.
