# MID4-206 MongoDB 적용 대비 k6 비교 테스트 보강

## 목적

활동내역 조회 API의 MongoDB Read Model 적용 전후를 같은 k6 조건으로 비교하기 위한 실행 기준을 정리한다.

관련 상위 작업은 `MID4-96`이며, 대상 API는 `GET /api/user-activities/{userId}`이다.

## 보강 범위

- 기존 단일 사용자 baseline은 유지하되, 문서 기준 VU 시나리오를 실행할 수 있게 한다.
- `K6_VARIANT=rdb|mongo` 값으로 RDB/MongoDB 측정 결과를 구분한다.
- `K6_TARGET_USER_IDS`와 `K6_USER_PICK_STRATEGY`로 multi-user 요청 분포를 구성한다.
- `smoke`, `baseline`, `average`, `high-load`, `stress`는 VU 기준으로 실행한다.
- `throughput`은 VU 기준 측정과 별도의 RPS 보강 시나리오로 유지한다.
- k6에서 HTTP status, JSON 파싱, 활동내역 핵심 필드 존재 여부를 검증한다.
- 이번 작업에서는 seed SQL 변경과 MongoDB Read Model 구현은 다루지 않는다.

`K6_VARIANT=rdb|mongo`와 실행 헬퍼의 `-Variant` 값은 결과 구분용 메타데이터다. 실제 RDB/MongoDB 구현 전환은 애플리케이션 실행 설정 또는 배포 상태에서 먼저 맞춘다.

## VU 시나리오

| 시나리오 | executor | 기본 부하 | 기본 시간 | 목적 |
| --- | --- | --- | --- | --- |
| `smoke` | `constant-vus` | 1 VU | 1분 | 스크립트, 인증, 응답 검증 |
| `baseline` | `constant-vus` | 20 VU | 5분 | 기본 성능 비교 |
| `average` | `constant-vus` | 50 VU | 10분 | 일반적인 동시 사용 상황 가정 |
| `high-load` | `constant-vus` | 100 VU | 10분 | 병목 발생 여부 확인 |
| `stress` | `ramping-vus` | 50 -> 100 -> 200 -> 400 VU | 각 3분 | 성능이 무너지는 지점 확인 |

위 값은 기존 문서의 범위 안에서 선택한 기본 대표값이다. VU 기준 시나리오는 iteration 사이에 `K6_SLEEP_SECONDS`를 적용한다. 기본값은 1초다.

## RPS 보강 시나리오

`throughput`은 문서의 VU 기준 시나리오와 별도로 요청 도착률을 고정해 처리량 한계를 확인할 때 사용한다.

| 시나리오 | executor | 기본 부하 | 기본 시간 | 목적 |
| --- | --- | --- | --- | --- |
| `throughput` | `constant-arrival-rate` | 50 rps | 1분 | RPS 기준 처리량 확인 |

비교 헬퍼의 기본 RPS 단계는 `50, 100, 150, 200, 250 rps`다.

## 실행 기준

RDB 기준 애플리케이션을 실행한 뒤 VU 시나리오를 실행한다.

```powershell
.\scripts\performance\activity-history\k6\run-mongodb-compare.ps1 `
  -Variant rdb `
  -Scenario baseline
```

MongoDB Read Model 기준 애플리케이션을 실행한 뒤 같은 조건으로 반복한다.

```powershell
.\scripts\performance\activity-history\k6\run-mongodb-compare.ps1 `
  -Variant mongo `
  -Scenario baseline `
  -MongoContainer sb13-monew-team04-mongodb-1
```

RPS 기준 처리량을 확인할 때만 `throughput`과 `Rates`를 사용한다.

```powershell
.\scripts\performance\activity-history\k6\run-mongodb-compare.ps1 `
  -Variant rdb `
  -Scenario throughput `
  -Rates 50,100,150,200,250 `
  -Duration 1m
```

여러 사용자 분포를 확인할 때는 사용자 ID 목록과 선택 전략을 함께 지정한다.

```powershell
.\scripts\performance\activity-history\k6\run-mongodb-compare.ps1 `
  -Variant rdb `
  -Scenario smoke `
  -TargetUserIds 00000001-0000-4000-8000-000000000001,00000001-0000-4000-8000-000000000002 `
  -UserPickStrategy round-robin
```

실측 전에는 애플리케이션 기동 확인과 smoke 실행을 워밍업으로 포함한다. smoke 결과는 응답 검증과 워밍업 근거로 별도 기록하고, baseline 이후의 VU/RPS 결과를 성능 판단에 사용한다.

## 판단 기준

MongoDB 적용 타당성 판단 기준은 `docs/mid4-96-mongodb-decision-record/08-rdb-performance-test-scenarios.md`의 임시 성공 기준을 따른다.

```text
http_req_failed < 1%
checks rate > 99%
dropped_iterations = 0
p95 < 200ms
p99 < 500ms
```

Postgres 컨테이너 CPU(docker stats)와 커넥션 풀 대기는 가능한 경우 함께 기록한다. 이 CPU 수치는 k6 컨테이너 CPU를 합산한 값이 아니며, Docker CPU 표기 기준이라 100%를 초과할 수 있다. 수집 환경이 준비되지 않아 측정하지 못한 경우에는 결과 표의 판단 칸에 미측정 사유를 남긴다.

통과/실패 판단은 위 k6 기준 지표인 `http_req_failed`, `checks rate`, `dropped_iterations`, `p95`, `p99`를 우선한다. `dial i/o timeout` 등 Request Failed warning은 기준 통과 여부와 별도로 잔여 리스크로 기록한다. 결과 표의 CPU 값은 별도 표기가 없으면 실행 중 `docker-stats-mid-*`로 남긴 순간 스냅샷이며 평균, 최대, 지속 사용률이 아니다. stress의 `30초 지점`처럼 표기한 값도 해당 시점 스냅샷으로 해석한다.

`p95 < 1000ms`, `p99 < 2000ms`는 MID4-179 처리량 한계 측정용 참고 기준으로만 사용한다.

## 2026-08-27~28 실행 결과

- 데이터는 `SEED_SCALE=10m` 기준으로 새로 적재했다.
- 대상 사용자는 기본 사용자 `00000001-0000-4000-8000-000000000001`이다.
- smoke는 단일 사용자, 2명 round-robin, 5명 round-robin 사용자 분포를 확인했다.
- 측정은 로컬 dev 애플리케이션과 Docker Postgres 기준이다.
- 추가 측정은 160~190 rps 경계값, 주요 시나리오 총 5회 반복, 100 rps 30분 soak, 150 rps 30분 soak, 190 rps 5분/30분 경계, 200/250/300 rps 원인 분리 기준으로 수행했다.
- `dev-default-debug`는 `application-dev.yaml` 기본 로그 조건이다. `com.codeit.sb13.monew=debug`, `org.hibernate.SQL=debug`, `org.hibernate.orm.jdbc.batch=trace`, `org.hibernate.orm.jdbc.bind=off`가 적용된다.
- `dev-sql-warn`은 같은 dev profile에서 `org.hibernate.SQL=warn`, `org.hibernate.orm.jdbc.batch=warn`, `org.hibernate.orm.jdbc.bind=off`를 애플리케이션 기동 인자로 override한 조건이다. 애플리케이션 패키지 로그는 dev profile 기본값인 `com.codeit.sb13.monew=debug`가 유지된다.
- 별도 override 기록이 없는 2026-08-27 초기 로컬 측정과 기존 200/250 rps 계열 결과는 `dev-default-debug` 참고값으로 취급한다. 특히 SQL DEBUG 출력이 많은 장시간/고부하 결과는 최종 처리량 판단에서 분리한다.
- 2026-08-28 추가 측정은 Java 프로세스 커맨드라인에서 `--logging.level.org.hibernate.SQL=warn --logging.level.org.hibernate.orm.jdbc.batch=warn --logging.level.org.hibernate.orm.jdbc.bind=off` 적용을 확인한 뒤 수행했다.
- 2026-08-28 후속 측정은 위 `dev-sql-warn` 조건에서 200/250/300 rps 동일 조건 3회 반복, 250/300 rps 10분 soak, stress 재측정, 5명 round-robin 200/250/300 rps로 수행했다.
- 로컬 결과 산출물은 `scripts/performance/activity-history/k6/results/mid4-206-mongodb-k6-compare/`에 저장되며 Git에는 포함하지 않는다.
- MongoDB Read Model 구현은 이번 작업 범위가 아니므로 실제 MongoDB 부하 측정은 수행하지 않았다.

## 로그 조건 분기 이유

초기 측정은 개발 중 SQL 확인과 응답 검증을 함께 보기 위해 dev profile 기본 로그 조건인 `dev-default-debug`로 실행했다. 이 조건은 SQL과 batch 동작을 확인하는 데 유용하지만, `org.hibernate.SQL=debug`와 `org.hibernate.orm.jdbc.batch=trace` 출력이 많아지면 장시간 또는 고부하 테스트에서 애플리케이션 로그 I/O가 응답 시간과 처리량에 영향을 줄 수 있다.

따라서 `dev-default-debug` 결과는 기존 동작 확인용 `debug-reference`로 보존하고, 최종 처리량 경계와 반복 측정 기준에서는 제외한다. `dev-sql-warn`은 같은 dev profile을 유지하되 SQL/배치 로그만 `warn`으로 낮춘 조건이며, MID4-206의 최종 RDB 기준값과 후속 테스트의 기본 로그 조건으로 사용한다. 이 조건은 prod 수준의 무로그 성능 측정이 아니라 SQL DEBUG/TRACE 출력 영향을 제거하기 위한 dev 기준 측정이다.

서로 다른 로그 조건의 결과는 직접 비교하지 않는다. 두 조건을 함께 볼 때는 SQL DEBUG/TRACE 출력이 부하 생성과 응답 시간에 끼친 영향을 분리하기 위한 참고 자료로만 해석한다.

## 테스트 결과 구분 기준

성능 결과는 `로그 조건`, `사용자 조건`, `preAllocatedVUs`, `maxVUs`, `시나리오`, `duration`, `실행 순서/워밍업 상태`가 같은 경우에만 직접 비교한다. 조건이 2개 이상 다른 결과끼리는 원인 판단에 사용하지 않는다.

결과 표의 `결과 용도`는 다음 기준으로 구분한다.

| 결과 용도 | 의미 |
| --- | --- |
| `final-baseline` | `dev-sql-warn`, 단일 사용자, `throughput`, `1m`, `preAllocatedVUs=500`, `maxVUs=500` 조건에서 워밍업 후 확인한 최종 RDB 기준값. 단일 실행값과 같은 조건 반복 집계값을 모두 이 용도로 기록한다. |
| `warmup-anomaly` | 같은 조건이지만 첫 실행에서 `Insufficient VUs`가 발생해 최종 기준에서 제외하는 값 |
| `debug-reference` | `application-dev.yaml` 기본 DEBUG 로그 조건에서 측정한 참고값 |
| `vu-headroom-reference` | `preAllocatedVUs=1000`, `maxVUs=1000`으로 VU 한도를 넓힌 원인 분리 참고값 |
| `boundary-reference` | 1분 고정 기준과 별도로 경계 부하를 5분 이상 유지해 본 참고값 |
| `multi-user-reference` | 여러 사용자 round-robin 분포를 확인한 참고값 |
| `soak-reference` | 장시간 유지 안정성을 확인한 참고값 |
| `stress-reference` | `dev-sql-warn` 조건에서 VU ramp stress를 재측정한 참고값 |
| `rdb-fanout-read` | `dev-sql-warn`, 10m seed, `fanout-overlay.sql`, read-only 조건에서 fan-out 1차 overlay 영향을 확인하는 값 |
| `rdb-fanout-boundary-reference` | `rdb-fanout-read`의 100 rps 이상 실패 후 같은 고정 조건에서 10~50 rps 하한 경계와 10분 유지 여부를 확인한 값 |
| `rdb-fanout-extreme-reference` | `fanout-extreme-overlay.sql`로 관심사 1개에 구독자 50,000명을 추가한 편중 조건 참고값 |
| `rdb-exclusion-filter-reference` | `dev-sql-warn`, 10m seed, `exclusion-overlay.sql`, read-only 조건에서 삭제/제외 후보 row 비용을 확인한 값 |
| `rdb-mixed-no-outbox` | `dev-sql-warn`, 10m seed, Outbox 미구현 상태에서 read/write 혼합 부하를 확인한 값 |
## VU 결과 기록

| variant | scenario | VU | duration | 로그 조건 | requests | RPS | p95 | p99 | error rate | checks rate | dropped iterations | Postgres 컨테이너 CPU(docker stats) | MongoDB 컨테이너 CPU(docker stats) | 커넥션 대기 | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| rdb | smoke | 1 | 1m | dev-sql-warn, fanout-overlay | 52 | 0.90 | 178.76ms | 647.35ms | 0.00% | 100.00% | 0 | N/A | N/A | 미계측 | seed 직후 첫 smoke, p99 기준 초과로 warmup-anomaly 처리 |
| rdb | smoke | 1 | 1m | dev-sql-warn, fanout-overlay | 52 | 0.91 | 187.53ms | 404.95ms | 0.00% | 100.00% | 0 | 0.00%(after) | N/A | after active 1, idle 11 | 워밍업 후 응답 검증 통과 |
| rdb | smoke | 1 | 1m | dev-sql-warn, fanout-overlay, boundary 재측정 | 52 | 0.91 | 160.67ms | 325.46ms | 0.00% | 100.00% | 0 | 0.05%(after) | N/A | after active 1, idle 10 | 통과, 새 10m seed 워밍업 smoke |
| rdb | smoke | 1 | 1m | dev-sql-warn, fanout-overlay, boundary 재측정 | 53 | 0.92 | 158.94ms | 168.43ms | 0.00% | 100.00% | 0 | 0.04%(after) | N/A | after active 1, idle 10 | 통과, throughput 진입 기준 |
| rdb | smoke | 1 | 1m | dev-sql-warn, fanout-extreme-overlay | 51 | 0.90 | 181.06ms | 182.04ms | 0.00% | 100.00% | 0 | 0.00%(after) | N/A | after active 1, idle 10 | 편중 관심사 reference smoke 통과 |
| rdb | smoke | 1 | 1m | dev-default-debug | 59 | 0.97 | 39.99ms | 47.71ms | 0.00% | 100.00% | 0 | 0.08% | N/A | active 1, idle 11 | 통과, 워밍업 및 응답 검증 |
| rdb | smoke | 1 | 1m | dev-default-debug, 2명 round-robin | 59 | 0.97 | 29.81ms | 33.40ms | 0.00% | 100.00% | 0 | 0.00% | N/A | active 1, idle 11 | 통과, 사용자 분포 확인 |
| rdb | smoke | 1 | 1m | dev-sql-warn, 5명 round-robin | 59 | 0.98 | 30.14ms | 204.84ms | 0.00% | 100.00% | 0 | N/A | N/A | 미계측 | 통과, 워밍업 및 응답 검증 |
| rdb | baseline | 20 | 5m | dev-default-debug | 5,880 | 19.50 | 26.84ms | 48.05ms | 0.00% | 100.00% | 0 | 5.15% | N/A | active 1, idle 11 | 통과 |
| rdb | average | 50 | 10m | dev-default-debug | 29,308 | 48.59 | 38.37ms | 81.65ms | 0.00% | 100.00% | 0 | 16.60% | N/A | active 1, idle 11 | 통과 |
| rdb | average | 50 | 10m | dev-sql-warn, 5명 round-robin | 29,400 | 49.16 | 31.19ms | 47.88ms | 0.00% | 100.00% | 0 | 13.25% | N/A | active 1, idle 11 | 통과, 다중 사용자 재측정 |
| rdb | high-load | 100 | 10m | dev-default-debug | 58,062 | 96.29 | 52.67ms | 109.44ms | 0.01% | 99.99% | 0 | 25.80% | N/A | active 1, idle 11 | 통과 |
| rdb | high-load | 100 | 10m | dev-sql-warn, 5명 round-robin | 58,334 | 97.50 | 38.76ms | 67.89ms | 0.02% | 99.98% | 0 | 31.31% | N/A | active 1, idle 11 | 통과, dial i/o timeout 10건 관찰 |
| rdb | stress | 50 -> 100 -> 200 -> 400 | 각 3m | dev-default-debug | 74,574 | 103.03 | 1,361.57ms | 1,595.57ms | 0.01% | 99.99% | 0 | 2.38% | N/A | active 1, idle 11 | 지연 기준 초과, timeout 경고 발생 |
| rdb | stress | 50 -> 100 -> 200 -> 400 | 각 3m | dev-sql-warn, 단일(1명, round-robin) | 94,678 | 130.41 | 101.35ms | 186.74ms | 0.01% | 99.99% | 0 | 2.10%(30초 지점) | N/A | active 1, idle 10, idle in transaction 1 | 수치 기준 통과, dial i/o timeout 14건 관찰 |

## 반복 측정 결과

baseline, average, high-load, 150 rps는 기존 1회 측정에 4회 추가 측정을 더해 총 5회 샘플로 집계했다. 200/250/300 rps는 `dev-sql-warn`, 단일 사용자, pre/max VU 500, 1분 조건을 고정한 2026-08-28 후속 3회 반복 결과다.

| scenario | samples | load | 로그 조건 | requests total | avg RPS | p95 min/avg/max | p99 min/avg/max | max error rate | min checks rate | max dropped | Request Failed warning | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| baseline | 5 | 20 VU, 5m | dev-default-debug | 29,340 | 19.50 | 26.84/30.41/32.96ms | 35.62/41.25/48.05ms | 0.00% | 100.00% | 0 | 0 | 반복 통과 |
| average | 5 | 50 VU, 10m | dev-default-debug | 146,002 | 48.57 | 35.50/37.18/39.75ms | 48.33/56.77/81.65ms | 0.03% | 99.97% | 0 | 10 | 수치 기준 통과, timeout 경고 관찰 |
| high-load | 5 | 100 VU, 10m | dev-default-debug | 291,096 | 96.90 | 44.05/50.12/55.15ms | 72.50/89.99/115.03ms | 0.01% | 99.99% | 0 | 7 | 수치 기준 통과, timeout 경고 관찰 |
| throughput | 5 | 150 rps, 1m | dev-default-debug | 45,003 | 150.12 | 21.73/35.09/87.21ms | 25.92/47.56/132.11ms | 0.00% | 100.00% | 0 | 0 | 반복 통과 |
| throughput | 3 | 200 rps, 1m, pre/max VU 500 | dev-sql-warn, 단일(1명, round-robin) | 36,001 | 205.94 | 20.90/24.30/30.12ms | 24.75/30.49/40.26ms | 0.00% | 100.00% | 0 | 0 | 3회 반복 통과, Postgres 컨테이너 CPU(docker stats) 55.02~63.44% |
| throughput | 3 | 250 rps, 1m, pre/max VU 500 | dev-sql-warn, 단일(1명, round-robin) | 45,002 | 257.22 | 22.67/26.63/29.05ms | 29.51/34.30/38.15ms | 0.00% | 100.00% | 0 | 0 | 3회 반복 통과, Postgres 컨테이너 CPU(docker stats) 69.96~78.97% |
| throughput | 3 | 300 rps, 1m, pre/max VU 500 | dev-sql-warn, 단일(1명, round-robin) | 54,003 | 308.77 | 24.35/55.63/106.98ms | 29.66/92.92/200.06ms | 0.00% | 100.00% | 0 | 0 | 3회 반복 통과, Postgres 컨테이너 CPU(docker stats) 94.32~124.29% |

## RPS 결과 기록

| variant | scenario | rate | duration | 로그 조건 | 사용자 조건 | preVU | maxVU | 결과 용도 | requests | RPS | p95 | p99 | error rate | checks rate | dropped iterations | Postgres 컨테이너 CPU(docker stats) | MongoDB 컨테이너 CPU(docker stats) | 커넥션 대기 | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| rdb | throughput | 10 rps | 1m | dev-sql-warn, fanout-overlay | 단일(1명, round-robin) | 500 | 500 | rdb-fanout-boundary-reference | 601 | 10.59 | 165.68ms | 196.45ms | 0.00% | 100.00% | 0 | 129.29% | N/A | active 3, idle 8 | 통과, 1분 기준 최고 통과 후보 |
| rdb | throughput | 20 rps | 1m | dev-sql-warn, fanout-overlay | 단일(1명, round-robin) | 500 | 500 | rdb-fanout-boundary-reference | 1,201 | 21.12 | 228.66ms | 281.29ms | 0.00% | 100.00% | 0 | 303.35% | N/A | active 4, idle 7 | 실패, p95 기준 초과 |
| rdb | throughput | 30 rps | 1m | dev-sql-warn, fanout-overlay | 단일(1명, round-robin) | 500 | 500 | rdb-fanout-boundary-reference | 1,801 | 27.11 | 11,951.05ms | 12,270.91ms | 0.00% | 100.00% | 0 | 917.09% | N/A | active 11 | 실패, latency 급증 |
| rdb | throughput | 40 rps | 1m | dev-sql-warn, fanout-overlay | 단일(1명, round-robin) | 500 | 500 | rdb-fanout-boundary-reference | 1,933 | 27.10 | 19,662.55ms | 20,367.61ms | 0.00% | 100.00% | 468 | 905.92% | N/A | active 10, idle in transaction 1 | 실패, VU 포화와 dropped iterations 발생 |
| rdb | throughput | 50 rps | 1m | dev-sql-warn, fanout-overlay | 단일(1명, round-robin) | 500 | 500 | rdb-fanout-boundary-reference | 1,839 | 25.56 | 21,448.99ms | 22,445.07ms | 0.00% | 100.00% | 1,161 | 920.58% | N/A | active 11 | 실패, VU 포화와 dropped iterations 발생 |
| rdb | throughput | 100 rps | 1m | dev-sql-warn, fanout-overlay | 단일(1명, round-robin) | 500 | 500 | rdb-fanout-read | 1,903 | 26.38 | 21,531.50ms | 22,825.07ms | 0.00% | 100.00% | 4,097 | 908.29% | N/A | active 10, idle in transaction 1 | 실패, VU 포화와 dropped iterations 발생 |
| rdb | throughput | 150 rps | 1m | dev-sql-warn, fanout-overlay | 단일(1명, round-robin) | 500 | 500 | rdb-fanout-read | 1,910 | 26.09 | 21,823.29ms | 22,448.29ms | 0.00% | 100.00% | 7,091 | 900.05% | N/A | active 10, idle 1 | 실패, VU 포화와 dropped iterations 발생 |
| rdb | throughput | 200 rps | 1m | dev-sql-warn, fanout-overlay | 단일(1명, round-robin) | 500 | 500 | rdb-fanout-read | 1,876 | 24.22 | 23,169.63ms | 24,358.33ms | 0.00% | 100.00% | 10,124 | 905.44% | N/A | active 11 | 실패, VU 포화와 dropped iterations 발생 |
| rdb | throughput | 50 rps | 1m | dev-default-debug | 단일(1명, round-robin) | 500 | 500 | debug-reference | 3,001 | 49.93 | 30.48ms | 33.79ms | 0.00% | 100.00% | 0 | 12.82% | N/A | active 1, idle 10, idle in transaction 1 | 참고 통과 |
| rdb | throughput | 100 rps | 1m | dev-default-debug | 단일(1명, round-robin) | 500 | 500 | debug-reference | 6,001 | 99.77 | 29.17ms | 33.34ms | 0.00% | 100.00% | 0 | 26.34% | N/A | active 1, idle 11 | 참고 통과 |
| rdb | throughput | 150 rps | 1m | dev-default-debug | 단일(1명, round-robin) | 500 | 500 | debug-reference | 9,001 | 150.10 | 87.21ms | 132.11ms | 0.00% | 100.00% | 0 | 52.89% | N/A | active 1, idle 10, idle in transaction 1 | 참고 통과 |
| rdb | throughput | 150 rps | 5m | dev-sql-warn | 5명 round-robin | 500 | 500 | multi-user-reference | 45,000 | 150.71 | 21.65ms | 25.56ms | 0.00% | 100.00% | 0 | 38.87% | N/A | active 1, idle 10, idle in transaction 1 | 다중 사용자 재측정 통과 |
| rdb | throughput | 160 rps | 1m | dev-default-debug | 단일(1명, round-robin) | 500 | 500 | debug-reference | 9,600 | 159.70 | 25.45ms | 43.91ms | 0.00% | 100.00% | 0 | 52.76% | N/A | active 2, idle 9, idle in transaction 1 | 참고 통과 |
| rdb | throughput | 170 rps | 1m | dev-default-debug | 단일(1명, round-robin) | 500 | 500 | debug-reference | 10,200 | 169.65 | 24.83ms | 40.84ms | 0.00% | 100.00% | 0 | 56.43% | N/A | active 1, idle 10, idle in transaction 1 | 참고 통과 |
| rdb | throughput | 180 rps | 1m | dev-default-debug | 단일(1명, round-robin) | 500 | 500 | debug-reference | 10,801 | 179.64 | 31.78ms | 58.70ms | 0.00% | 100.00% | 0 | 61.86% | N/A | active 1, idle 10, idle in transaction 1 | 참고 통과 |
| rdb | throughput | 190 rps | 1m | dev-default-debug | 단일(1명, round-robin) | 500 | 500 | debug-reference | 11,401 | 189.63 | 70.80ms | 321.32ms | 0.00% | 100.00% | 0 | 68.67% | N/A | active 2, idle 9, idle in transaction 1 | 참고 통과, Postgres 컨테이너 CPU(docker stats) 70% 근접 |
| rdb | throughput | 190 rps | 5m | dev-sql-warn | 단일(1명, round-robin) | 500 | 500 | boundary-reference | 57,000 | 190.70 | 32.81ms | 42.47ms | 0.01% | 99.99% | 0 | 57.62% | N/A | active 1, idle 9, idle in transaction 2 | 5분 경계 통과, dial i/o timeout 7건 관찰 |
| rdb | throughput | 200 rps | 1m | dev-default-debug | 단일(1명, round-robin) | 500 | 500 | debug-reference | 10,967 | 178.96 | 3,198.98ms | 3,361.01ms | 0.00% | 100.00% | 1,034 | 56.29% | N/A | active 1, idle 8, idle in transaction 3 | 참고 실패, VU 부족 및 DEBUG 로그 영향 가능성 |
| rdb | throughput | 200 rps | 1m | dev-sql-warn | 단일(1명, round-robin) | 1000 | 1000 | vu-headroom-reference | 12,000 | 204.30 | 46.49ms | 162.73ms | 0.00% | 100.00% | 0 | 58.40% | N/A | active 1, idle 7, idle in transaction 4 | 참고 통과 |
| rdb | throughput | 200 rps | 1m | dev-sql-warn | 단일(1명, round-robin) | 500 | 500 | warmup-anomaly | 11,855 | 202.61 | 294.49ms | 2,654.48ms | 0.00% | 100.00% | 145 | 54.55% | N/A | active 3, idle 8, idle in transaction 1 | 첫 실행 `Insufficient VUs` 1건, 최종 기준 제외 |
| rdb | throughput | 200 rps | 1m | dev-sql-warn | 단일(1명, round-robin) | 500 | 500 | final-baseline | 12,001 | 205.15 | 20.51ms | 24.04ms | 0.00% | 100.00% | 0 | 52.82% | N/A | active 2, idle 8, idle in transaction 2 | 재실행 통과 |
| rdb | throughput | 250 rps | 1m | dev-default-debug | 단일(1명, round-robin) | 500 | 500 | debug-reference | 12,734 | 202.58 | 2,737.13ms | 3,027.10ms | 0.00% | 100.00% | 2,266 | 86.68% | N/A | active 1, idle 11 | 참고 실패, VU 부족 및 DEBUG 로그 영향 가능성 |
| rdb | throughput | 250 rps | 1m | dev-sql-warn | 단일(1명, round-robin) | 1000 | 1000 | vu-headroom-reference | 15,001 | 255.18 | 163.52ms | 441.06ms | 0.00% | 100.00% | 0 | 85.94% | N/A | active 1, idle 8, idle in transaction 3 | 참고 통과, Postgres 컨테이너 CPU(docker stats) 70% 초과 |
| rdb | throughput | 250 rps | 1m | dev-sql-warn | 단일(1명, round-robin) | 500 | 500 | final-baseline | 15,001 | 256.68 | 24.34ms | 29.55ms | 0.00% | 100.00% | 0 | 76.85% | N/A | active 2, idle 8, idle in transaction 2 | 응답 기준 통과, Postgres 컨테이너 CPU(docker stats) 70% 초과 |
| rdb | throughput | 300 rps | 1m | dev-sql-warn | 단일(1명, round-robin) | 1000 | 1000 | vu-headroom-reference | 18,001 | 306.36 | 613.79ms | 858.04ms | 0.00% | 100.00% | 0 | 126.29% | N/A | active 1, idle 10, idle in transaction 1 | 참고 실패, VU 부족 없이 p95/p99 기준 초과 |
| rdb | throughput | 300 rps | 1m | dev-sql-warn | 단일(1명, round-robin) | 500 | 500 | final-baseline | 18,001 | 307.50 | 24.63ms | 31.71ms | 0.00% | 100.00% | 0 | 91.81% | N/A | active 3, idle 9 | 응답 기준 통과, Postgres 컨테이너 CPU(docker stats) 70% 초과 |
| rdb | throughput | 200 rps | 1m | dev-sql-warn | 5명 round-robin | 500 | 500 | multi-user-reference | 12,001 | 204.63 | 27.76ms | 33.93ms | 0.00% | 100.00% | 0 | 47.21% | N/A | active 1, idle 10, idle in transaction 1 | 다중 사용자 1분 통과 |
| rdb | throughput | 250 rps | 1m | dev-sql-warn | 5명 round-robin | 500 | 500 | multi-user-reference | 15,000 | 260.75 | 30.42ms | 40.73ms | 0.00% | 100.00% | 0 | 74.15% | N/A | active 3, idle 8, idle in transaction 1 | 다중 사용자 1분 통과, Postgres 컨테이너 CPU(docker stats) 70% 초과 |
| rdb | throughput | 300 rps | 1m | dev-sql-warn | 5명 round-robin | 500 | 500 | multi-user-reference | 18,001 | 309.95 | 40.23ms | 87.79ms | 0.00% | 100.00% | 0 | 80.99% | N/A | active 5, idle 6, idle in transaction 1 | 다중 사용자 1분 통과, Postgres 컨테이너 CPU(docker stats) 70% 초과 |

## Soak 결과 기록

| variant | scenario | rate | duration | 로그 조건 | 사용자 조건 | preVU | maxVU | requests | RPS | p95 | p99 | error rate | checks rate | dropped iterations | Postgres 컨테이너 CPU(docker stats) | MongoDB 컨테이너 CPU(docker stats) | 커넥션 대기 | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| rdb | throughput-soak | 100 rps | 30m | dev-default-debug | 단일(1명, round-robin) | 500 | 500 | 180,000 | 100.14 | 26.71ms | 31.04ms | 0.04% | 99.96% | 0 | 28.65% | N/A | active 1, idle 11 | 수치 기준 통과, timeout 경고 80건 관찰 |
| rdb | throughput-soak | 150 rps | 30m | dev-default-debug | 단일(1명, round-robin) | 500 | 500 | 223,079 | 122.23 | 3,156.80ms | 29,750.32ms | 0.88% | 99.12% | 46,828 | 76.08% | N/A | active 1, idle 8, idle in transaction 3 | 비교 제외, SQL DEBUG 로그 출력 영향 가능성, Request Failed 1,955건 |
| rdb | throughput-soak | 150 rps | 30m | dev-sql-warn | 단일(1명, round-robin) | 500 | 500 | 270,001 | 150.50 | 28.40ms | 35.13ms | 0.07% | 99.93% | 0 | 38.73% | N/A | active 2, idle 10 | 수치 기준 통과, dial i/o timeout 183건 관찰 |
| rdb | throughput-soak | 190 rps | 30m | dev-sql-warn | 단일(1명, round-robin) | 500 | 500 | 342,001 | 191.89 | 37.29ms | 52.11ms | 0.02% | 99.98% | 0 | 57.98% | N/A | active 2, idle 10 | 수치 기준 통과, dial i/o timeout 53건 관찰 |
| rdb | throughput-soak | 250 rps | 10m | dev-sql-warn | 단일(1명, round-robin) | 500 | 500 | 150,001 | 257.65 | 49.41ms | 136.01ms | 0.02% | 99.98% | 0 | 82.73% | N/A | active 1, idle 9, idle in transaction 2 | 수치 기준 통과, dial i/o timeout 28건 관찰 |
| rdb | throughput-soak | 300 rps | 10m | dev-sql-warn | 단일(1명, round-robin) | 500 | 500 | 178,841 | 307.25 | 1,692.74ms | 1,934.22ms | 0.02% | 99.98% | 1,159 | 116.15% | N/A | active 1, idle 11 | 실패, dropped/latency 기준 초과, `Insufficient VUs` 1건, dial i/o timeout 30건 |
| rdb | throughput-soak | 10 rps | 10m | dev-sql-warn, fanout-overlay | 단일(1명, round-robin) | 500 | 500 | 6,000 | 10.60 | 164.37ms | 187.57ms | 0.00% | 100.00% | 0 | 131.29% | N/A | active 3, idle 8 | `rdb-fanout-boundary-reference`, 10분 soak 통과 |

이번 RDB 기준에서는 VU 시나리오가 high-load까지 성공 기준을 만족했다. `dev-default-debug` stress는 400 VU ramp 구간에서 p95/p99가 임시 성공 기준을 초과했고, `dev-sql-warn` stress 재측정은 p95/p99와 dropped 기준을 통과했지만 dial i/o timeout 14건이 관찰됐다. stress의 Postgres 컨테이너 CPU(docker stats)는 30초 지점 스냅샷이라 peak CPU로 해석하지 않는다.

`dev-sql-warn`, 단일 사용자, `preAllocatedVUs=500`, `maxVUs=500`, `1m` 고정 조건에서는 200 rps 첫 실행에서 `Insufficient VUs` 1건과 dropped 145가 발생했다. 이후 같은 조건 3회 반복에서는 200/250/300 rps 모두 dropped 0, error 0.00%, checks 100.00%였고 p95/p99 응답 기준을 통과했다. 다만 250 rps부터 Postgres 컨테이너 CPU(docker stats)가 70% 전후 또는 초과로 올라갔고, 300 rps는 124.29% 스냅샷까지 관찰되어 장시간 기준에서는 보수적으로 봐야 한다. `pre/max VU 1000`으로 수행한 기존 300 rps 실패값은 조건과 실행 순서가 달라 `vu-headroom-reference`로만 남기며, `pre/max VU 500` 최종 기준과 직접 원인 비교하지 않는다.

10분 soak에서는 250 rps가 p95 49.41ms, p99 136.01ms, dropped 0으로 수치 기준을 통과했지만 timeout 28건과 Postgres 컨테이너 CPU(docker stats) 82.73%가 관찰됐다. 300 rps 10분은 dropped 1,159와 p95/p99 기준 초과로 실패했다. 따라서 현재 로컬 RDB 기준 30분 장시간 통과 구간은 190 rps, 10분 참고 통과 구간은 250 rps, 단기 응답 기준 통과 확인 구간은 300 rps까지이며, 운영성 관점의 보수적 경계는 250 rps 이상부터 별도 최적화 또는 MongoDB Read Model 비교 대상으로 본다.

fan-out boundary 재측정에서는 같은 10m seed와 `fanout-overlay.sql` 조건에서 10 rps 1분과 10 rps 10분 soak가 통과했다. 20 rps 1분은 p95 228.66ms로 실패했고, 30 rps부터 응답 시간이 초 단위로 급증했으며, 40/50 rps는 VU 500 포화와 dropped iterations가 함께 발생했다. 따라서 fan-out 1차 overlay 조건의 현재 문서화된 지속 통과 기준은 10 rps이고, 20 rps 이상은 쿼리 최적화 또는 MongoDB Read Model 비교 대상으로 본다.

## 제외 조건 필터 비용 결과

`exclusion-overlay.sql`은 10m seed 위에 target user의 최신 삭제 댓글 10,000건, 최신 좋아요 대상 삭제 댓글 10,000건, 최신 조회 대상 삭제 기사 10,000건, target user 구독 관심사 50개에 삭제 사용자 구독 50,000건을 추가한다. 이 overlay는 fan-out overlay와 동시에 적용하지 않는다.

EXPLAIN 수동 실행에서 확인한 핵심 비용은 다음과 같다.

| 조회 | 주요 증가 지점 | 실행 시간 | 판단 |
| --- | --- | ---: | --- |
| 최근 작성 댓글 | `idx_comments_user_created_id`에서 삭제 댓글 후보 10,000건 필터 | 1.560ms | 최신 삭제 댓글을 지나도 10건 탐색 비용은 낮음 |
| 최근 좋아요 댓글 | `idx_comment_likes_liked_by_created_id` 10,010건, `pk_comments` 10,010회 확인 | 8.944ms | 삭제 댓글 join 반복 비용이 생김 |
| 최근 조회 기사 | `idx_article_views_user_viewed_id` 10,010건, `pk_articles` 10,010회 확인 | 11.834ms | 삭제 기사 join 반복 비용이 생김 |
| 구독 관심사 | `pk_users subscriber` 50,545회 확인 | 68.116ms | 삭제 사용자 제외 조건 비용이 가장 큼 |

| scenario | 부하 | 시간 | requests | RPS | p95 | p99 | error rate | checks rate | dropped iterations | Postgres CPU | 커넥션 대기 | 판단 |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | --- |
| smoke | 1 VU | 1분 | 54 | 0.94 | 126.29ms | 493.31ms | 0.00% | 100.00% | 0 | N/A | 미계측 | 통과, p99 기준 근접 |
| smoke | 1 VU | 1분 | 55 | 0.96 | 110.38ms | 112.87ms | 0.00% | 100.00% | 0 | N/A | 미계측 | 통과, throughput 진입 기준 |
| throughput | 10 rps | 1분 | 601 | 10.65 | 107.89ms | 123.09ms | 0.00% | 100.00% | 0 | 76.12% | active 2, idle 9 | 통과 |
| throughput | 20 rps | 1분 | 1,200 | 21.30 | 119.60ms | 146.44ms | 0.00% | 100.00% | 0 | 154.97% | active 3, idle 8 | 통과 |
| throughput | 30 rps | 1분 | 1,800 | 31.94 | 134.35ms | 163.87ms | 0.00% | 100.00% | 0 | 249.81% | active 5, idle 6 | 통과 |
| throughput | 40 rps | 1분 | 2,401 | 42.55 | 142.15ms | 182.72ms | 0.00% | 100.00% | 0 | 336.70% | active 5, idle 5, idle in transaction 1 | 통과 |
| throughput | 50 rps | 1분 | 3,001 | 53.19 | 188.02ms | 223.08ms | 0.00% | 100.00% | 0 | 484.12% | active 7, idle 4 | 통과, 단기 기준 최고 통과 |
| throughput-soak | 50 rps | 10분 | 30,001 | 53.28 | 228.00ms | 279.56ms | 0.00% | 100.00% | 0 | 492.74% | active 7, idle 4 | 실패, p95 기준 초과 |

exclusion overlay는 50 rps 1분까지 dropped 없이 통과했지만, 50 rps 10분 soak에서는 p95 228.00ms로 기준을 초과했다. 삭제 후보 row를 많이 지나가는 조건에서는 단기 처리량보다 지속 p95와 Postgres CPU 스냅샷을 함께 봐야 한다.

## Read/Write Mixed 결과

mixed 스크립트는 `K6_SCRIPT=mixed`로 실행하며 `activity-history-read`, `comment-create`, `comment-like-toggle`, `article-view`, `subscription-toggle` API를 `80/20` 또는 `50/50` 비율로 호출한다. `throughput`의 rate는 k6 iteration 도착률이고, toggle API는 한 iteration에서 `POST`와 `DELETE`를 연속 호출하므로 실제 HTTP RPS는 requested rate보다 높게 기록된다.

쓰기 요청으로 DB 상태가 변하므로 공식 mixed 측정은 각 시나리오 직전에 10m seed를 새로 적재한 결과만 직접 비교 대상으로 본다. smoke 이후 이어서 실행한 80/20 average 초기 검증값은 공식 표에서 제외한다.

| mix ratio | scenario | 부하 | 시간 | requests | HTTP RPS | p95 | p99 | error rate | checks rate | dropped iterations | Postgres CPU | 커넥션 대기 | 판단 |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | --- |
| 80/20 | smoke | 5 VU | 1분 | 323 | 5.68 | 32.61ms | 54.21ms | 0.00% | 100.00% | 0 | N/A | 미계측 | 통과 |
| 80/20 | average | 50 VU | 10분 | 32,200 | 57.26 | 30.55ms | 52.60ms | 0.01% | 99.99% | 0 | 23.87% | active 2, idle 10 | 통과 |
| 80/20 | throughput | 100 iter/s | 1분 | 6,630 | 118.03 | 23.59ms | 30.03ms | 0.00% | 100.00% | 0 | 39.57% | active 3, idle 9 | 통과 |
| 80/20 | throughput | 150 iter/s | 1분 | 9,881 | 175.87 | 22.09ms | 26.58ms | 0.00% | 100.00% | 0 | 44.72% | active 2, idle 9, idle in transaction 1 | 통과 |
| 50/50 | throughput | 100 iter/s | 1분 | 7,499 | 133.53 | 19.95ms | 23.84ms | 0.00% | 100.00% | 0 | 26.35% | active 1, idle 9, idle in transaction 1 | 통과 |

공식 mixed 측정에서는 80/20 average 10분, 80/20 100/150 iter/s, 50/50 100 iter/s 모두 p95/p99, error rate, checks rate, dropped 기준을 통과했다. 현재 Outbox 구현은 없으므로 이 값은 `rdb-mixed-no-outbox` reference이며, Outbox 구현 후 같은 seed와 같은 script 조건으로 재측정해야 한다.

## 완료 확인

- [x] `docker compose -f compose.k6.yaml config`가 통과한다.
- [x] 각 시나리오의 k6 options가 문서 기준 executor와 부하로 생성된다.
- [x] smoke 시나리오에서 응답 구조 검증이 통과한다.
- [x] 결과 산출물 구분 정보에 `variant`, `scenario`, VU 또는 rate가 포함된다.
- [x] 결과에는 `variant`, `scenario`, `targetUserCount`, `userPickStrategy`, VU 또는 rate, p95, p99, error rate, checks rate, dropped iterations가 포함된다.
- [x] 워밍업을 포함해 RDB 기준 smoke, baseline, average, high-load, stress, throughput 측정을 완료했다.
- [x] RDB 기준 160~190 rps 경계값 측정을 완료했다.
- [x] RDB 기준 baseline, average, high-load, 150 rps를 총 5회 샘플로 반복 측정했다.
- [x] RDB fan-out lower-bound 10~50 rps와 10 rps 10분 soak 측정을 완료했다.
- [x] RDB 기준 100 rps 30분 soak 측정을 완료했다.
- [x] RDB 기준 SQL DEBUG 로그를 낮춘 조건에서 150 rps 30분 soak 측정을 완료했다.
- [x] RDB 기준 SQL DEBUG 로그를 낮춘 조건에서 190 rps 5분 경계 측정을 완료했다.
- [x] RDB 기준 SQL DEBUG 로그를 낮춘 조건에서 5명 round-robin average/high-load/150 rps 재측정을 완료했다.
- [x] RDB 기준 SQL DEBUG 로그를 낮춘 조건에서 190 rps 30분 soak 측정을 완료했다.
- [x] RDB 기준 SQL DEBUG 로그를 낮춘 조건과 pre/max VU 1000으로 200/250/300 rps VU 한도 참고 측정을 완료했다.
- [x] RDB 기준 SQL DEBUG 로그를 낮춘 조건과 pre/max VU 500으로 200/250/300 rps 최종 기준 재측정을 완료했다.
- [x] 200 rps 첫 실행 `warmup-anomaly`와 같은 조건 재실행 `final-baseline` 결과를 분리했다.
- [x] 기존 `dev-default-debug` 결과, `dev-sql-warn` 재측정 결과, VU 한도 참고 결과를 표에서 분리했다.
- [x] RDB 기준 테스트 결과 구분 기준을 문서화했다.
- [x] RDB 기준 `dev-sql-warn` 조건과 pre/max VU 500으로 200/250/300 rps 동일 조건 3회 반복 측정을 완료했다.
- [x] RDB 기준 `dev-sql-warn` 조건과 pre/max VU 500으로 250/300 rps 10분 soak 측정을 완료했다.
- [x] RDB 기준 `dev-sql-warn` 조건에서 stress 재측정을 완료했다.
- [x] RDB 기준 `dev-sql-warn` 조건과 5명 round-robin으로 200/250/300 rps 1분 측정을 완료했다.
- [x] 신규 실행 raw 로그의 timeout warning을 간단 확인했다.
- [x] RDB 제외 조건 overlay와 EXPLAIN 확인을 완료했다.
- [x] RDB 제외 조건 overlay에서 10/20/30/40/50 rps와 50 rps 10분 soak를 측정했다.
- [x] read/write mixed k6 스크립트와 실행 헬퍼 옵션을 추가했다.
- [x] RDB read/write mixed 80/20 smoke, 80/20 average, 80/20 throughput 100/150 iter/s, 50/50 throughput 100 iter/s를 측정했다.
- [x] mixed 공식 결과는 각 시나리오 직전 10m seed를 새로 적재한 값으로 분리했다.

MongoDB 실제 비교 측정은 MongoDB Read Model 구현과 애플리케이션 전환 설정이 준비된 뒤 같은 시나리오로 반복한다.

fan-out worst-case, 제외 조건 필터 비용, read/write 혼합 부하 조건과 결과는 [RDB 조회 성능 측정 시나리오](./mid4-96-mongodb-decision-record/08-rdb-performance-test-scenarios.md#후속-테스트-공통-고정-조건)의 고정 조건과 결과 표에도 함께 기록한다. MongoDB 실제 비교 측정은 MongoDB Read Model 구현과 애플리케이션 전환 설정이 준비된 뒤 같은 조건으로 반복한다.
