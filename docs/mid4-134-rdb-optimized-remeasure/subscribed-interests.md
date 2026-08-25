# 구독 중인 관심사 재측정

## 원자료

- SQL template: [raw/sql-template.sql](raw/sql-template.sql)
- 100k: [raw/sql-100k.out](raw/sql-100k.out)
- 1m: [raw/sql-1m.out](raw/sql-1m.out)
- 10m: [raw/sql-10m.out](raw/sql-10m.out)

## 측정 기준

구독 중인 관심사는 main query와 keywords batch query를 분리해 기록했다. total은 main 반복 실행 시간과 keywords 반복 실행 시간을 같은 순번끼리 더한 뒤 median을 계산했다.

## Main query 측정값

| seed scale | baseline EXPLAIN | optimized EXPLAIN | baseline repeats | optimized repeats | baseline median | optimized median |
| --- | ---: | ---: | --- | --- | ---: | ---: |
| `100k` | `5.517 ms` | `5.249 ms` | `3.951`, `3.803`, `4.980`, `4.414`, `4.327 ms` | `4.022`, `3.982`, `3.685`, `4.236`, `3.922 ms` | `4.327 ms` | `3.982 ms` |
| `1m` | `2.666 ms` | `1.648 ms` | `2.961`, `2.884`, `2.772`, `2.762`, `2.634 ms` | `1.180`, `1.200`, `1.121`, `1.116`, `1.171 ms` | `2.772 ms` | `1.171 ms` |
| `10m` | `10.052 ms` | `0.791 ms` | `11.051`, `10.541`, `10.535`, `10.878`, `10.810 ms` | `1.009`, `1.107`, `0.945`, `0.940`, `0.903 ms` | `10.810 ms` | `0.945 ms` |

## Keywords query 측정값

| seed scale | baseline EXPLAIN | optimized EXPLAIN | baseline repeats | optimized repeats | baseline median | optimized median |
| --- | ---: | ---: | --- | --- | ---: | ---: |
| `100k` | `0.099 ms` | `0.292 ms` | `0.538`, `0.449`, `0.446`, `0.450`, `0.517 ms` | `0.769`, `0.584`, `0.484`, `0.589`, `0.461 ms` | `0.450 ms` | `0.584 ms` |
| `1m` | `0.047 ms` | `0.148 ms` | `0.451`, `0.464`, `0.572`, `0.585`, `0.292 ms` | `0.582`, `0.369`, `0.295`, `0.288`, `0.299 ms` | `0.464 ms` | `0.299 ms` |
| `10m` | `0.050 ms` | `0.114 ms` | `0.517`, `0.391`, `0.496`, `0.596`, `0.486 ms` | `0.380`, `0.576`, `0.384`, `0.353`, `0.312 ms` | `0.496 ms` | `0.380 ms` |

## Total median 비교

| seed scale | baseline total repeats | optimized total repeats | baseline median | optimized median | delta | change |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| `100k` | `4.472`, `4.481`, `4.332`, `4.380`, `5.195 ms` | `4.791`, `4.566`, `4.169`, `4.825`, `4.383 ms` | `4.472 ms` | `4.566 ms` | `0.094 ms` | `2.10%` |
| `1m` | `4.708`, `4.280`, `3.549`, `3.453`, `3.331 ms` | `1.762`, `1.569`, `1.416`, `1.404`, `1.470 ms` | `3.549 ms` | `1.470 ms` | `-2.079 ms` | `-58.58%` |
| `10m` | `11.635`, `11.394`, `12.454`, `14.413`, `11.481 ms` | `1.389`, `1.683`, `1.329`, `1.293`, `1.215 ms` | `11.635 ms` | `1.329 ms` | `-10.306 ms` | `-88.58%` |

## 실행계획 원문 위치

각 scale별 raw 파일의 다음 구간에 `EXPLAIN (ANALYZE, BUFFERS)` 원문을 기록했다.

- `query=subscribed_interests_main explain`
- `query=subscribed_interests_keywords explain`

## 해석 보류

병목 여부와 남는 후보 판단은 사용자 해석 예정.
