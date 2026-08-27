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

DB CPU와 커넥션 풀 대기는 가능한 경우 함께 기록한다. 수집 환경이 준비되지 않아 측정하지 못한 경우에는 결과 표의 판단 칸에 미측정 사유를 남긴다.

`p95 < 1000ms`, `p99 < 2000ms`는 MID4-179 처리량 한계 측정용 참고 기준으로만 사용한다.

## 2026-08-27 실행 결과

- 데이터는 `SEED_SCALE=10m` 기준으로 새로 적재했다.
- 대상 사용자는 기본 사용자 `00000001-0000-4000-8000-000000000001`이다.
- smoke는 단일 사용자와 2명 round-robin 사용자 분포를 모두 확인했다.
- 측정은 로컬 dev 애플리케이션과 Docker Postgres 기준이다.
- 추가 측정은 160~190 rps 경계값, 주요 시나리오 총 5회 반복, 100 rps 30분 soak 기준으로 수행했다.
- raw k6 로그와 summary JSON은 `scripts/performance/activity-history/k6/results/mid4-206-mongodb-k6-compare/`에 저장되며 Git에는 포함하지 않는다.
- MongoDB Read Model 구현은 이번 작업 범위가 아니므로 실제 MongoDB 부하 측정은 수행하지 않았다.

## VU 결과 기록

| variant | scenario | VU | duration | requests | RPS | p95 | p99 | error rate | checks rate | dropped iterations | Postgres CPU | MongoDB CPU | 커넥션 대기 | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| rdb | smoke | 1 | 1m | 59 | 0.97 | 39.99ms | 47.71ms | 0.00% | 100.00% | 0 | 0.08% | N/A | active 1, idle 11 | 통과, 워밍업 및 응답 검증 |
| rdb | smoke | 1 | 1m | 59 | 0.97 | 29.81ms | 33.40ms | 0.00% | 100.00% | 0 | 0.00% | N/A | active 1, idle 11 | 통과, 2명 round-robin 사용자 분포 확인 |
| rdb | baseline | 20 | 5m | 5,880 | 19.50 | 26.84ms | 48.05ms | 0.00% | 100.00% | 0 | 5.15% | N/A | active 1, idle 11 | 통과 |
| rdb | average | 50 | 10m | 29,308 | 48.59 | 38.37ms | 81.65ms | 0.00% | 100.00% | 0 | 16.60% | N/A | active 1, idle 11 | 통과 |
| rdb | high-load | 100 | 10m | 58,062 | 96.29 | 52.67ms | 109.44ms | 0.01% | 99.99% | 0 | 25.80% | N/A | active 1, idle 11 | 통과 |
| rdb | stress | 50 -> 100 -> 200 -> 400 | 각 3m | 74,574 | 103.03 | 1,361.57ms | 1,595.57ms | 0.01% | 99.99% | 0 | 2.38% | N/A | active 1, idle 11 | 지연 기준 초과, timeout 경고 발생 |

## 반복 측정 결과

기존 1회 측정에 4회 추가 측정을 더해 총 5회 샘플로 집계했다.

| scenario | samples | load | requests total | avg RPS | p95 min/avg/max | p99 min/avg/max | max error rate | min checks rate | max dropped | Request Failed warning | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| baseline | 5 | 20 VU, 5m | 29,340 | 19.50 | 26.84/30.41/32.96ms | 35.62/41.25/48.05ms | 0.00% | 100.00% | 0 | 0 | 반복 통과 |
| average | 5 | 50 VU, 10m | 146,002 | 48.57 | 35.50/37.18/39.75ms | 48.33/56.77/81.65ms | 0.03% | 99.97% | 0 | 10 | 수치 기준 통과, timeout 경고 관찰 |
| high-load | 5 | 100 VU, 10m | 291,096 | 96.90 | 44.05/50.12/55.15ms | 72.50/89.99/115.03ms | 0.01% | 99.99% | 0 | 7 | 수치 기준 통과, timeout 경고 관찰 |
| throughput | 5 | 150 rps, 1m | 45,003 | 150.12 | 21.73/35.09/87.21ms | 25.92/47.56/132.11ms | 0.00% | 100.00% | 0 | 0 | 반복 통과 |

## RPS 결과 기록

| variant | scenario | rate | duration | requests | RPS | p95 | p99 | error rate | checks rate | dropped iterations | Postgres CPU | MongoDB CPU | 커넥션 대기 | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| rdb | throughput | 50 rps | 1m | 3,001 | 49.93 | 30.48ms | 33.79ms | 0.00% | 100.00% | 0 | 12.82% | N/A | active 1, idle 10, idle in transaction 1 | 통과 |
| rdb | throughput | 100 rps | 1m | 6,001 | 99.77 | 29.17ms | 33.34ms | 0.00% | 100.00% | 0 | 26.34% | N/A | active 1, idle 11 | 통과 |
| rdb | throughput | 150 rps | 1m | 9,001 | 150.10 | 87.21ms | 132.11ms | 0.00% | 100.00% | 0 | 52.89% | N/A | active 1, idle 10, idle in transaction 1 | 통과 |
| rdb | throughput | 160 rps | 1m | 9,600 | 159.70 | 25.45ms | 43.91ms | 0.00% | 100.00% | 0 | 52.76% | N/A | active 2, idle 9, idle in transaction 1 | 통과 |
| rdb | throughput | 170 rps | 1m | 10,200 | 169.65 | 24.83ms | 40.84ms | 0.00% | 100.00% | 0 | 56.43% | N/A | active 1, idle 10, idle in transaction 1 | 통과 |
| rdb | throughput | 180 rps | 1m | 10,801 | 179.64 | 31.78ms | 58.70ms | 0.00% | 100.00% | 0 | 61.86% | N/A | active 1, idle 10, idle in transaction 1 | 통과 |
| rdb | throughput | 190 rps | 1m | 11,401 | 189.63 | 70.80ms | 321.32ms | 0.00% | 100.00% | 0 | 68.67% | N/A | active 2, idle 9, idle in transaction 1 | 단기 경계 통과, DB CPU 70% 근접 |
| rdb | throughput | 200 rps | 1m | 10,967 | 178.96 | 3,198.98ms | 3,361.01ms | 0.00% | 100.00% | 1,034 | 56.29% | N/A | active 1, idle 8, idle in transaction 3 | 실패, VU 부족 및 지연/dropped 기준 초과 |
| rdb | throughput | 250 rps | 1m | 12,734 | 202.58 | 2,737.13ms | 3,027.10ms | 0.00% | 100.00% | 2,266 | 86.68% | N/A | active 1, idle 11 | 실패, VU 부족 및 지연/dropped 기준 초과 |

## Soak 결과 기록

| variant | scenario | rate | duration | requests | RPS | p95 | p99 | error rate | checks rate | dropped iterations | Postgres CPU | MongoDB CPU | 커넥션 대기 | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| rdb | throughput-soak | 100 rps | 30m | 180,000 | 100.14 | 26.71ms | 31.04ms | 0.04% | 99.96% | 0 | 28.65% | N/A | active 1, idle 11 | 수치 기준 통과, timeout 경고 80건 관찰 |

이번 RDB 기준에서는 VU 시나리오가 high-load까지 성공 기준을 만족했다. stress에서는 400 VU ramp 구간에서 p95/p99가 임시 성공 기준을 초과하고 timeout 경고가 발생했다. RPS 기준 1분 측정은 190 rps까지 통과했지만 DB CPU가 70%에 근접하고 p99가 321.32ms까지 상승했다. 200 rps부터는 k6가 최대 500 VU에 도달하면서 `dropped_iterations`가 발생했다. 반복 안정 구간은 150 rps, 단기 경계 통과 구간은 190 rps로 본다.

## 완료 확인

- [x] `docker compose -f compose.k6.yaml config`가 통과한다.
- [x] 각 시나리오의 k6 options가 문서 기준 executor와 부하로 생성된다.
- [x] smoke 시나리오에서 응답 구조 검증이 통과한다.
- [x] 결과 summary 파일명에 `variant`, `scenario`, VU 또는 rate가 포함된다.
- [x] 결과에는 `variant`, `scenario`, `targetUserCount`, `userPickStrategy`, VU 또는 rate, p95, p99, error rate, checks rate, dropped iterations가 포함된다.
- [x] 워밍업을 포함해 RDB 기준 smoke, baseline, average, high-load, stress, throughput 측정을 완료했다.
- [x] RDB 기준 160~190 rps 경계값 측정을 완료했다.
- [x] RDB 기준 baseline, average, high-load, 150 rps를 총 5회 샘플로 반복 측정했다.
- [x] RDB 기준 100 rps 30분 soak 측정을 완료했다.

MongoDB 실제 비교 측정은 MongoDB Read Model 구현과 애플리케이션 전환 설정이 준비된 뒤 같은 시나리오로 반복한다.
