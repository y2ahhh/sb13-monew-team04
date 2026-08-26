# MID4-96 MongoDB/Redis 적용 여부 판단 기록

## 결론

이번 배포 전에는 활동내역 조회에 MongoDB Read Model과 Redis를 적용하지 않는다.

MID4-125와 MID4-179 측정 결과 기준으로 RDB 인덱스 최적화 후 `GET /api/user-activities/{userId}`는 10m seed에서 `200 rps`까지 안정적으로 처리됐다. 현재 근거만으로는 MongoDB 환경 구성, Outbox, projection 동기화, 삭제 전파, 장애 재처리 정책을 배포 전에 추가할 만큼의 성능 병목이 확인되지 않았다.

이 디렉터리의 `01`~`08` 문서는 MongoDB 적용 가능성을 검토하기 위해 작성한 사전 설계 기록이다. 최종 적용 여부는 이 README, MID4-125, MID4-179 측정 결과를 기준으로 판단한다.

따라서 이번 단계의 의사결정은 다음과 같다.

| 항목 | 결정 |
| --- | --- |
| MongoDB Read Model | 후순위 |
| Redis | 미적용 |
| RDB | Source of Truth로 유지 |
| 배포 전 작업 | RDB 최적화 상태 유지 |

## 재검토 조건

다음 조건 중 하나가 확인되면 MongoDB Read Model 적용을 다시 검토한다.

- 활동내역 API 목표 처리량이 `250 rps` 이상으로 확정된다.
- 현재보다 엄격한 p95/p99 SLO가 정해지고 RDB 최적화 상태에서 기준을 넘는다.
- 구독 관심사 fan-out worst-case에서 `interestSubscriberCount` 또는 keywords 조립 비용이 병목으로 확인된다.
- RDB 인덱스와 SQL 구조를 재검증한 뒤에도 특정 활동내역 조회가 병목으로 남는다.

## 근거 문서

| 문서 | 내용 |
| --- | --- |
| [01-overview-and-selection.md](./01-overview-and-selection.md) | MongoDB 사용 목적과 적용 대상 선정 기준 |
| [02-mongodb-storage-model.md](./02-mongodb-storage-model.md) | 적용 시 `activity_histories`와 snapshot 저장 모델 후보 |
| [03-event-handler-targets.md](./03-event-handler-targets.md) | 적용 시 필요한 이벤트 핸들러 후보 |
| [04-outbox-design.md](./04-outbox-design.md) | RDB 원본 변경과 MongoDB 반영을 분리하기 위한 Outbox 설계 |
| [05-count-aggregation-policy.md](./05-count-aggregation-policy.md) | count 집계값 반영 기준 |
| [06-final-flow-and-conclusion.md](./06-final-flow-and-conclusion.md) | 후속 적용 검토 흐름과 판단 기준 |
| [07-rdb-test-data-policy.md](./07-rdb-test-data-policy.md) | RDB 기준 테스트 데이터 생성 기준 |
| [08-rdb-performance-test-scenarios.md](./08-rdb-performance-test-scenarios.md) | RDB 조회 성능 측정 시나리오 |

## 관련 문서

- [MID4-125 MongoDB Read Model 적용 대상 선정](../mid4-125-mongodb-read-model-target-selection/README.md)
- [MID4-132 RDB baseline 성능 측정](../mid4-132-activity-history-rdb-baseline/README.md)
- [MID4-134 RDB 최적화 후 성능 재측정](../mid4-134-rdb-optimized-remeasure/README.md)
- [MID4-179 RDB 최적화 후 최대 요청량 측정](../mid4-179-rdb-throughput-limit/README.md)
