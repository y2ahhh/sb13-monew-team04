# 최근 좋아요한 댓글 재측정

## 원자료

- SQL template: [raw/sql-template.sql](raw/sql-template.sql)
- 100k: [raw/sql-100k.out](raw/sql-100k.out)
- 1m: [raw/sql-1m.out](raw/sql-1m.out)
- 10m: [raw/sql-10m.out](raw/sql-10m.out)

## 측정값

| seed scale | baseline EXPLAIN | optimized EXPLAIN | baseline repeats | optimized repeats | baseline median | optimized median |
| --- | ---: | ---: | --- | --- | ---: | ---: |
| `100k` | `8.936 ms` | `0.168 ms` | `9.066`, `9.725`, `11.235`, `9.524`, `8.936 ms` | `0.896`, `0.921`, `0.759`, `0.708`, `0.809 ms` | `9.524 ms` | `0.809 ms` |
| `1m` | `10.515 ms` | `0.184 ms` | `12.238`, `11.750`, `12.216`, `11.807`, `11.632 ms` | `1.025`, `1.300`, `1.022`, `1.172`, `0.754 ms` | `11.807 ms` | `1.025 ms` |
| `10m` | `44.102 ms` | `0.209 ms` | `48.387`, `52.918`, `45.801`, `44.662`, `45.905 ms` | `0.900`, `0.782`, `0.836`, `0.709`, `0.851 ms` | `45.905 ms` | `0.836 ms` |

## Median 비교

| seed scale | baseline median | optimized median | delta | change |
| --- | ---: | ---: | ---: | ---: |
| `100k` | `9.524 ms` | `0.809 ms` | `-8.715 ms` | `-91.51%` |
| `1m` | `11.807 ms` | `1.025 ms` | `-10.782 ms` | `-91.32%` |
| `10m` | `45.905 ms` | `0.836 ms` | `-45.069 ms` | `-98.18%` |

## 실행계획 원문 위치

각 scale별 raw 파일의 `query=recent_liked_comments explain` 구간에 `EXPLAIN (ANALYZE, BUFFERS)` 원문을 기록했다.

## 해석 보류

병목 여부와 남는 후보 판단은 사용자 해석 예정.
