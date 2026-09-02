# MID4-244 측정 조건과 재현 방법

> [MID4-244 요약](README.md) · [API 실패 경계 상세 결과](api-boundary-results.md) · [활동내역 성능 문서 통합 안내서](../activity-history-performance-guide.md)

이 문서는 MID4-244 성능 측정의 공통 조건, 데이터 준비 순서, 재현 명령과 검증 결과를 기록한다. 핵심 성능 판단은 요약 문서에서, 회차별 API 결과는 API 실패 경계 상세 문서에서 확인한다.

## 한눈에 보기

- SQL은 워밍업 3회 뒤 일반 실행과 실행계획을 각각 5회 측정하고 일반 실행의 가운데 값을 비교했다.
- 1분 API는 같은 요청량을 3회 측정했고, 10분 API는 같은 요청량으로 한 번 지속 측정했다.
- 합격 기준은 오류율 `<1%`, 검사 성공률 `>99%`, 요청 누락 `0`, p95 `<200 ms`, p99 `<500 ms`다.
- 가공하지 않은 결과와 로그는 Git에서 제외했으며 후보 인덱스와 후속 `COUNT(*)` 확인 결과는 코드와 Flyway에 반영하지 않았다.

## 동일하게 맞춘 측정 조건

| 항목 | 값 |
| --- | --- |
| Jira | `MID4-244` |
| 기준 코드 | `5f7e8ec929d54aafa552ad17292df36aad0f7b94` |
| 데이터 몰림 조건 요청량 상향 측정 기준 코드 | `aeb6bf3d522d1945500608206ac1ffcc039e4575` |
| 일반 데이터 요청량 상향 측정 기준 코드 | `8019310` |
| 측정 시각 | 2026-09-01~02 KST |
| API | `GET /api/user-activities/{userId}` |
| 대상 사용자 | `00000001-0000-4000-8000-000000000001` |
| DB 데이터 | `SEED_SCALE=10m` 기본 데이터와 데이터 몰림 조건을 만들기 위한 추가 데이터 |
| SQL 반복 | 워밍업 3회, 일반 실행 5회, 실행계획 5회 |
| SQL 비교값 | 일반 실행 5회의 가운데 값(median) |
| API 워밍업 | 각 측정 전 `1 VU · 1분` |
| 1분 API | 같은 요청량을 3회 실행하고 실행 사이 30초 안정화, 가운데 값 비교 |
| 10분 API | 같은 요청량으로 1회 지속 측정 |
| 합격 기준 | 오류율 `<1%`, 검사 성공률 `>99%`, 요청 누락 `0`, p95 `<200 ms`, p99 `<500 ms` |

최초 인덱스 비교의 데이터 준비 방식은 다음과 같다. 데이터 몰림 조건은 MID4-227 재측정 DB를 재사용했다. 데이터 몰림 2배 조건과 일반 조건은 각각 새 DB에 기본 데이터를 다시 적재했다.

같은 SQL을 기존 인덱스만 사용한 상태와 후보 인덱스를 추가한 상태에서 동일한 데이터로 비교했다. 각 상태의 SQL을 따로 워밍업했고, 기존 인덱스 다음 후보 인덱스 순으로 실행했다. API는 후보 인덱스를 추가한 상태를 먼저 측정했다. 이후 후보 인덱스를 제거하고 기존 인덱스만 있는 상태를 측정했으며, 두 상태 모두 별도로 워밍업했다.

데이터 몰림 조건의 요청량 상향 측정은 기존 DB를 재사용하지 않고 아래 순서로 다시 준비했다.

1. 전용 Docker 프로젝트의 DB와 볼륨을 제거하고 새로 생성했다.
2. 애플리케이션 스키마를 적용하고 `SEED_SCALE=10m` 데이터를 새로 적재했다.
3. MID4-227과 동일한 수량으로 데이터 몰림 조건을 만들기 위한 데이터를 추가하고 실제 건수를 확인했다.
4. 부분 커버링 인덱스 4개를 적용한 뒤 `ANALYZE`를 실행했다.
5. 디버그 모드에서 API를 한 번 호출해 실제 SQL이 실행되는지 확인했다.
6. 성능 모드로 애플리케이션을 다시 시작하고 `1 VU · 1분` 워밍업을 실행했다.
7. 이미 통과한 더 낮은 RPS는 제외하고 `200 rps`부터 요청량을 올렸다. 각 RPS는 1분씩 3회 실행하고 사이에 30초를 두었다.

`200~250 rps` 측정 후 같은 DB와 인덱스는 유지했다. 애플리케이션만 성능 모드로 다시 시작하고 `1 VU · 1분` 워밍업을 다시 수행한 뒤 `275 rps`, `300 rps`를 이어서 측정했다.

일반 데이터의 처리량 한계는 데이터 몰림 실험과 분리해 아래 순서로 측정했다.

1. 전용 Docker 프로젝트의 DB와 볼륨을 제거하고 `SEED_SCALE=10m` 기본 데이터를 새로 적재했다.
2. 데이터 몰림·제외 조건을 만들기 위한 추가 데이터와 후보 인덱스는 적용하지 않았다.
3. 디버그 모드에서 API를 한 번 호출해 실제 SQL을 확인했다.
4. 애플리케이션을 성능 모드로 다시 시작하고 `1 VU · 1분` 워밍업을 실행했다.
5. 이미 10분 통과한 `300 rps`는 반복하지 않고 `350 rps`, `400 rps`를 각각 1분 3회 측정했다. `400 rps`가 2회 실패한 시점에서 `450 rps`, `500 rps`는 실행하지 않았다.
6. `350 rps`와 `400 rps` 사이의 `375 rps`는 새 DB와 같은 준비 순서로 1분 3회 측정했다.
7. 1분 3회를 모두 통과한 `350 rps`는 같은 기본 데이터 DB를 유지하고 애플리케이션 재시작과 워밍업을 다시 한 뒤 10분 동안 확인했다.

## 재현 명령

SQL 비교:

```powershell
.\scripts\performance\activity-history\mid4-244\run-sql-index-compare.ps1 `
  -PgContainer <postgres-container> `
  -WarmupRuns 3 `
  -MeasureRuns 5 `
  -ResultSet mid4-244-rdb-index-compare
```

실패 경계 API 측정:

```powershell
.\scripts\performance\activity-history\mid4-244\run-failed-boundary.ps1 `
  -Boundary fanout-200-1m `
  -PgContainer <postgres-container> `
  -IndexVariant partial `
  -FanoutMultiplier 1 `
  -ResultSet mid4-244-rdb-partial-fanout
```

새 DB를 준비하고 데이터 몰림 조건과 후보 인덱스를 적용한 뒤 워밍업하는 요청량 상향 측정:

```powershell
.\scripts\performance\activity-history\mid4-227\run-api-compare.ps1 `
  -BuildLabel after `
  -Overlay fanout `
  -AppJar <application-jar> `
  -AppCommit <commit-sha> `
  -Ticket MID4-244 `
  -ProjectNameOverride monew-perf-244-controlled-partial-fanout `
  -ResultSetOverride mid4-244-rdb-partial-fanout-limit-controlled `
  -PostOverlaySql .\scripts\performance\activity-history\mid4-244\partial-covering-indexes.sql `
  -ThroughputRates @(200, 205, 210, 225, 250, 275, 300) `
  -ThroughputRepeatCount 3 `
  -StabilizationSeconds 30 `
  -SkipSoak
```

일반 데이터의 300 rps 초과 경계 측정:

```powershell
.\scripts\performance\activity-history\mid4-227\run-api-compare.ps1 `
  -BuildLabel after `
  -Overlay general `
  -AppJar <application-jar> `
  -AppCommit <commit-sha> `
  -Ticket MID4-244 `
  -ProjectNameOverride monew-perf-244-general-limit-coarse `
  -ResultSetOverride mid4-244-rdb-general-limit-coarse `
  -ThroughputRates @(350, 400, 450, 500) `
  -ThroughputRepeatCount 3 `
  -StabilizationSeconds 30 `
  -StopOnRepeatedFailure `
  -SkipSoak
```

준비된 같은 DB를 재사용한 350 rps 10분 지속 측정:

```powershell
.\scripts\performance\activity-history\mid4-227\run-api-compare.ps1 `
  -BuildLabel after `
  -Overlay general `
  -AppJar <application-jar> `
  -AppCommit <commit-sha> `
  -Ticket MID4-244 `
  -ProjectNameOverride <prepared-compose-project> `
  -ResultSetOverride mid4-244-rdb-general-limit-soak `
  -DbPort <prepared-db-port> `
  -ReuseDatabase `
  -SkipThroughputMatrix `
  -SoakRate 350
```

데이터 몰림 2배 조건은 추가 데이터를 넣을 때 `FANOUT_MULTIPLIER=2`로 지정한다.

```powershell
Get-Content -Raw .\scripts\performance\activity-history\fanout-overlay.sql |
  docker exec -i <postgres-container> psql -U monew -d monew -v ON_ERROR_STOP=1 -v FANOUT_MULTIPLIER=2
```

가공하지 않은 JSON, 실행계획, 애플리케이션 로그는 `scripts/performance/activity-history/k6/results/` 아래에 저장하며 Git에는 포함하지 않는다.

## 검증 결과

- `2026-09-01`: `.\gradlew.bat test` 통과
- `2026-09-01`: `.\gradlew.bat clean build` 통과
- `2026-09-01~02`: 관련 PowerShell 측정 스크립트 구문 검사 통과
- `2026-09-01`: MID4-244 원시 결과가 `.gitignore`의 `scripts/performance/**/k6/results/` 규칙으로 제외되는지 확인
- `2026-09-02`: 새 DB·데이터 몰림 조건·후보 인덱스·워밍업 순서로 `200~300 rps` 상향 측정 완료
- `2026-09-02`: `.\gradlew.bat clean build` 통과
- `2026-09-02`: 새 일반 데이터 DB와 워밍업 조건에서 `350`, `375`, `400 rps`를 3회씩 측정하고 첫 반복 실패에서 중단
- `2026-09-02`: 일반 데이터 `350 rps · 10분` 실패와 기존 `300 rps · 10분` 통과 결과를 비교해 지속 처리량 경계 기록
- `2026-09-02`: Hibernate `COUNT(별칭)`/`COUNT(*)` 생성 SQL과 기사 조회 ACTIVE 부분 인덱스의 `INCLUDE(id)` 유무를 비교하고 실험 인덱스 제거 확인

## 완료 조건

- [x] 두 기존 실패 구간의 현재 `develop` 기준 결과를 기록했다.
- [x] SQL별 워밍업 3회, 일반 실행 5회, 실행계획 5회로 비교했다.
- [x] 후보 인덱스 적용 전후 실행 시간, 처리 행 수, Heap Fetches와 버퍼 사용량을 기록했다.
- [x] 최종 후보를 실패 구간에서 재측정하고 합격 여부를 기록했다.
- [x] 통과 또는 가운데 값 통과인 경우에만 다음 경계를 측정하고 최초 반복 실패 지점에서 중단했다.
- [x] 데이터 몰림 조건을 새로 준비하고 워밍업한 뒤 200 rps부터 처리량 경계를 확인했다.
- [x] 일반 데이터에서 이미 통과한 300 rps를 제외하고 초과 구간의 단기·지속 처리량 경계를 확인했다.
- [x] 실제 SQL과 PostgreSQL 실행계획 원문을 기록했다.
- [x] 기사 조회 수 집계에서 `COUNT(*)`와 `INCLUDE(id)` 유무에 따른 실행계획, heap 접근과 인덱스 크기를 비교했다.
- [x] 가공하지 않은 결과와 로그가 Git에서 제외되는지 확인했다.
- [x] 테스트와 빌드 결과를 기록했다.
