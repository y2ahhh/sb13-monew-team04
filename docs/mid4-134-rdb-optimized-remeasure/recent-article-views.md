# 최근 조회 기사 재측정

## 원자료

- SQL template: [raw/sql-template.sql](raw/sql-template.sql)
- 100k: [raw/sql-100k.out](raw/sql-100k.out)
- 1m: [raw/sql-1m.out](raw/sql-1m.out)
- 10m: [raw/sql-10m.out](raw/sql-10m.out)

## 측정값

| seed scale | baseline EXPLAIN | optimized EXPLAIN | baseline repeats | optimized repeats | baseline median | optimized median |
| --- | ---: | ---: | --- | --- | ---: | ---: |
| `100k` | `25.568 ms` | `0.173 ms` | `25.981`, `27.132`, `27.095`, `27.150`, `26.654 ms` | `0.650`, `0.659`, `1.170`, `0.746`, `0.521 ms` | `27.095 ms` | `0.659 ms` |
| `1m` | `235.410 ms` | `0.163 ms` | `236.583`, `226.549`, `231.003`, `234.893`, `226.372 ms` | `0.573`, `0.519`, `0.634`, `0.473`, `0.508 ms` | `231.003 ms` | `0.519 ms` |
| `10m` | `1820.813 ms` | `0.335 ms` | `1825.932`, `1805.697`, `1861.183`, `1813.368`, `1832.137 ms` | `0.713`, `0.550`, `0.537`, `1.108`, `0.525 ms` | `1825.932 ms` | `0.550 ms` |

## Median 비교

| seed scale | baseline median | optimized median | delta | change |
| --- | ---: | ---: | ---: | ---: |
| `100k` | `27.095 ms` | `0.659 ms` | `-26.436 ms` | `-97.57%` |
| `1m` | `231.003 ms` | `0.519 ms` | `-230.484 ms` | `-99.78%` |
| `10m` | `1825.932 ms` | `0.550 ms` | `-1825.382 ms` | `-99.97%` |

## 실행계획 원문 위치

각 scale별 raw 파일의 `query=recent_article_views explain` 구간에 `EXPLAIN (ANALYZE, BUFFERS)` 원문을 기록했다.

## 해석 보류

병목 여부와 남는 후보 판단은 사용자 해석 예정.
