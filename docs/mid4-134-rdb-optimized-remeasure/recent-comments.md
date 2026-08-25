# 최근 작성 댓글 재측정

## 원자료

- SQL template: [raw/sql-template.sql](raw/sql-template.sql)
- 100k: [raw/sql-100k.out](raw/sql-100k.out)
- 1m: [raw/sql-1m.out](raw/sql-1m.out)
- 10m: [raw/sql-10m.out](raw/sql-10m.out)

## 측정값

| seed scale | baseline EXPLAIN | optimized EXPLAIN | baseline repeats | optimized repeats | baseline median | optimized median |
| --- | ---: | ---: | --- | --- | ---: | ---: |
| `100k` | `7.736 ms` | `0.136 ms` | `8.953`, `8.983`, `11.553`, `9.165`, `8.712 ms` | `0.673`, `0.432`, `0.529`, `0.589`, `0.386 ms` | `8.983 ms` | `0.529 ms` |
| `1m` | `12.836 ms` | `0.140 ms` | `14.151`, `13.587`, `13.720`, `17.696`, `14.005 ms` | `0.518`, `0.437`, `0.414`, `0.401`, `0.441 ms` | `14.005 ms` | `0.437 ms` |
| `10m` | `88.314 ms` | `0.155 ms` | `80.545`, `82.747`, `95.963`, `93.401`, `78.940 ms` | `0.910`, `0.580`, `0.646`, `0.594`, `0.450 ms` | `82.747 ms` | `0.594 ms` |

## Median 비교

| seed scale | baseline median | optimized median | delta | change |
| --- | ---: | ---: | ---: | ---: |
| `100k` | `8.983 ms` | `0.529 ms` | `-8.454 ms` | `-94.11%` |
| `1m` | `14.005 ms` | `0.437 ms` | `-13.568 ms` | `-96.88%` |
| `10m` | `82.747 ms` | `0.594 ms` | `-82.153 ms` | `-99.28%` |

## 실행계획 원문 위치

각 scale별 raw 파일의 `query=recent_comments explain` 구간에 `EXPLAIN (ANALYZE, BUFFERS)` 원문을 기록했다.

## 해석 보류

병목 여부와 남는 후보 판단은 사용자 해석 예정.
