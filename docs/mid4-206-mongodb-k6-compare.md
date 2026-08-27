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

## VU 결과 기록

| variant | scenario | VU | duration | requests | RPS | p95 | p99 | error rate | checks rate | dropped iterations | Postgres CPU | MongoDB CPU | 커넥션 대기 | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| rdb | smoke | 1 | 1m |  |  |  |  |  |  |  |  |  |  |  |
| rdb | baseline | 20 | 5m |  |  |  |  |  |  |  |  |  |  |  |
| rdb | average | 50 | 10m |  |  |  |  |  |  |  |  |  |  |  |
| rdb | high-load | 100 | 10m |  |  |  |  |  |  |  |  |  |  |  |
| rdb | stress | 50 -> 100 -> 200 -> 400 | 각 3m |  |  |  |  |  |  |  |  |  |  |  |
| mongo | smoke | 1 | 1m |  |  |  |  |  |  |  |  |  |  |  |
| mongo | baseline | 20 | 5m |  |  |  |  |  |  |  |  |  |  |  |
| mongo | average | 50 | 10m |  |  |  |  |  |  |  |  |  |  |  |
| mongo | high-load | 100 | 10m |  |  |  |  |  |  |  |  |  |  |  |
| mongo | stress | 50 -> 100 -> 200 -> 400 | 각 3m |  |  |  |  |  |  |  |  |  |  |  |

## RPS 결과 기록

| variant | scenario | rate | duration | requests | RPS | p95 | p99 | error rate | checks rate | dropped iterations | Postgres CPU | MongoDB CPU | 커넥션 대기 | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| rdb | throughput | 50 rps | 1m |  |  |  |  |  |  |  |  |  |  |  |
| rdb | throughput | 100 rps | 1m |  |  |  |  |  |  |  |  |  |  |  |
| rdb | throughput | 150 rps | 1m |  |  |  |  |  |  |  |  |  |  |  |
| rdb | throughput | 200 rps | 1m |  |  |  |  |  |  |  |  |  |  |  |
| rdb | throughput | 250 rps | 1m |  |  |  |  |  |  |  |  |  |  |  |
| mongo | throughput | 50 rps | 1m |  |  |  |  |  |  |  |  |  |  |  |
| mongo | throughput | 100 rps | 1m |  |  |  |  |  |  |  |  |  |  |  |
| mongo | throughput | 150 rps | 1m |  |  |  |  |  |  |  |  |  |  |  |
| mongo | throughput | 200 rps | 1m |  |  |  |  |  |  |  |  |  |  |  |
| mongo | throughput | 250 rps | 1m |  |  |  |  |  |  |  |  |  |  |  |

## 완료 확인

- `docker compose -f compose.k6.yaml config`가 통과한다.
- 각 시나리오의 k6 options가 문서 기준 executor와 부하로 생성된다.
- smoke 시나리오에서 응답 구조 검증이 통과한다.
- RDB와 MongoDB 결과 summary가 서로 다른 파일명으로 저장된다.
- 결과에는 `variant`, `scenario`, `targetUserCount`, `userPickStrategy`, VU 또는 rate, p95, p99, error rate, checks rate, dropped iterations가 포함된다.
