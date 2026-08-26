# Outbox 설계

[상위 문서](./README.md) | [이전: 이벤트 핸들러 대상](./03-event-handler-targets.md) | [다음: 카운트 집계 처리 기준](./05-count-aggregation-policy.md)

## 동시성 및 Outbox 처리

MongoDB Read Model을 비동기로 반영하려면 RDB 원본 변경과 MongoDB 반영 사이의 정합성 문제가 생긴다.

가장 피해야 할 방식은 RDB 저장과 MongoDB 저장을 요청 흐름에서 각각 직접 수행하는 것이다.

```text
RDB 저장 성공
MongoDB 저장 실패
-> RDB에는 원본 데이터가 있지만 MongoDB 활동내역에는 누락될 수 있다.

RDB 저장 실패
MongoDB 저장 성공
-> 원본에는 없는 데이터가 MongoDB 활동내역에 남을 수 있다.
```

따라서 MongoDB Read Model을 후속 적용하게 된다면 Transactional Outbox Pattern을 기본 설계 후보로 둔다.

```text
RDB 트랜잭션 안
-> 원본 데이터 변경
-> outbox_events 테이블에 이벤트 저장

RDB 트랜잭션 밖
-> outbox worker가 이벤트를 읽어 MongoDB Read Model 반영
```

중요한 점은 Outbox가 MongoDB 반영을 같은 트랜잭션에서 수행하기 위한 방식이 아니라는 것이다.

Outbox는 RDB 원본 변경과 이벤트 발생 사실을 같은 트랜잭션으로 보장하고, MongoDB 반영은 커밋 이후 별도 worker가 비동기로 처리하기 위한 방식이다.

요청 처리 흐름은 다음과 같이 둔다.

```text
사용자 요청
-> RDB 트랜잭션 시작
-> 댓글/기사/관심사/좋아요 등 원본 데이터 변경
-> 도메인 이벤트 수집
-> outbox_events 테이블에 이벤트 저장
-> RDB 커밋
-> 사용자 response 반환
-> outbox worker가 이벤트 조회
-> MongoDB Read Model 저장 또는 갱신
```

### Outbox 테이블 구조

Outbox 이벤트는 현재 프로젝트의 엔티티 ID 전략에 맞춰 JPA 엔티티로 관리하고, PK는 UUID를 사용한다.

Outbox 저장은 원본 엔티티 변경과 같은 RDB 트랜잭션에 참여해야 하므로 JPA Repository로 저장하는 것을 기본으로 한다.

```text
outbox_events

id              UUID PK
event_type      VARCHAR(80)
aggregate_type  VARCHAR(50)
aggregate_id    UUID
actor_user_id   UUID NULL
payload_json    JSON/JSONB
status          VARCHAR(20)
retry_count     INT
next_retry_at   TIMESTAMP NULL
occurred_at     TIMESTAMP
processed_at    TIMESTAMP NULL
last_error      TEXT NULL
created_at      TIMESTAMP
updated_at      TIMESTAMP
```

`source_version`은 바로 확정하지 않고 보류 컬럼으로 둔다.

```text
source_version  BIGINT NULL
```

이 컬럼은 이벤트 순서 역전 방지에는 유용하지만, 사용하려면 댓글, 기사, 관심사 같은 원본 엔티티에 version 필드를 먼저 추가해야 한다. 엔티티 변경 범위와 낙관적 락 예외 처리까지 함께 검토해야 하므로 초기 Outbox 기본 구조에는 포함하지 않는다.

각 컬럼의 의미는 다음과 같다.

```text
id
-> Outbox 이벤트 식별자
-> 별도 event_id 컬럼을 두지 않고 이 값을 멱등성 기준으로 사용한다.

event_type
-> 이벤트 종류
-> 예: COMMENT_WRITTEN, ARTICLE_VIEWED, INTEREST_UNSUBSCRIBED

aggregate_type
-> 원본 도메인 종류
-> 예: COMMENT, ARTICLE, INTEREST

aggregate_id
-> 원본 엔티티 ID

actor_user_id
-> 이벤트를 발생시킨 사용자 ID
-> 시스템 배치나 사용자 주체가 없는 이벤트는 NULL을 허용한다.

payload_json
-> MongoDB 문서가 아니라 도메인 이벤트 payload
-> 운영 DB가 PostgreSQL이면 JSONB, MySQL이면 JSON 사용을 우선한다.
-> 테스트 DB나 호환성 제약으로 JSON 타입 사용이 어렵다면 TEXT fallback을 허용한다.
-> TEXT fallback을 사용할 때는 애플리케이션에서 JSON 직렬화/역직렬화 검증을 수행한다.
-> worker가 event_type을 기준으로 해석해 MongoDB Read Model에 반영한다.

status
-> Outbox 처리 상태
-> PENDING, PROCESSED, FAILED, DEAD_LETTER를 기본 상태로 사용한다.

retry_count
-> worker 처리 실패 횟수

next_retry_at
-> FAILED 이벤트를 다시 처리할 수 있는 시각
-> PENDING 상태에서는 NULL을 허용한다.

occurred_at
-> 도메인 이벤트가 발생한 시각

processed_at
-> MongoDB Read Model 반영이 완료된 시각

last_error
-> 마지막 처리 실패 원인

created_at, updated_at
-> Outbox row 생성 및 수정 시각
```

초기 worker는 단일 worker 기준으로 단순하게 시작한다.

```text
PENDING 이벤트 또는 next_retry_at이 지난 FAILED 이벤트 조회
-> created_at ASC 순서로 처리
-> MongoDB Read Model 반영 성공 시 PROCESSED
-> 실패 시 FAILED, retry_count 증가, next_retry_at 설정, last_error 기록
-> 최대 재시도 횟수 초과 시 DEAD_LETTER로 전환
```

UUID는 순서 기준으로 사용하지 않는다. 처리 순서는 `created_at` 또는 `occurred_at` 기준으로 둔다.

초기 재시도 정책은 다음과 같이 둔다.

```text
max_retry_count = 5

1회 실패 후 next_retry_at = now + 1분
2회 실패 후 next_retry_at = now + 5분
3회 실패 후 next_retry_at = now + 15분
4회 실패 후 next_retry_at = now + 1시간
5회 실패 시 DEAD_LETTER
```

`DEAD_LETTER` 이벤트는 worker가 자동 재처리하지 않는다. 운영자가 `last_error`와 원본 데이터를 확인한 뒤 수동으로 상태를 `PENDING`으로 되돌리거나 별도 보정 작업으로 처리한다.

worker 조회를 위한 권장 인덱스는 다음과 같다.

```text
status, next_retry_at, created_at
```

worker 조회 조건은 상태와 처리 가능 시각을 기준으로 둔다. payload 내부 필드를 조회 조건으로 사용하지 않는 한 별도 JSON path/index는 만들지 않는다.

여러 worker를 동시에 운영해야 하는 경우에는 같은 이벤트를 여러 worker가 동시에 처리하지 않도록 JPQL/native query 또는 DB lock 전략을 별도로 검토한다.

### Source Version 검토 기준

`source_version`은 원본 엔티티 변경 이벤트가 순서 역전으로 MongoDB snapshot을 잘못 덮어쓰는 문제를 줄이기 위한 값이다.

하지만 현재 엔티티에 version 필드가 없다면 Outbox에 `source_version`만 추가해도 의미가 약하다. 따라서 다음 항목을 별도로 검토한 뒤 도입 여부를 결정한다.

```text
- 댓글 수정/삭제 이벤트가 순서 역전으로 MongoDB snapshot을 잘못 덮어쓸 위험이 큰지
- 기사 수정/삭제 이벤트에도 같은 문제가 있는지
- 관심사 키워드 변경/삭제 이벤트에도 version 기준이 필요한지
- JPA @Version 도입이 기존 수정 로직에 영향을 주는지
- 낙관적 락 예외 처리를 프로젝트 범위에서 감당할 수 있는지
```

초기 구현에서는 `source_version` 없이 Outbox 이벤트를 저장하고, 단일 worker가 `created_at ASC` 기준으로 처리한다.

### 대안과 트레이드오프

```text
1. 같은 트랜잭션에서 원본 변경 + outbox 이벤트 저장

장점
- RDB 변경과 이벤트 저장이 함께 보장된다.
- 서버 장애가 발생해도 커밋된 이벤트를 나중에 재처리할 수 있다.
- MongoDB Read Model 누락을 복구할 근거가 남는다.

단점
- 사용자 응답 전에 outbox insert 1회가 추가된다.

판단
- 후속 적용 시 정합성 근거가 필요하므로 기본안 후보로 둔다.
```

```text
2. 원본 변경 후 이벤트 저장도 비동기로 처리

장점
- 사용자 response 영향이 가장 적다.

단점
- response 반환 후 이벤트 저장 전에 서버 장애가 발생하면 이벤트가 유실될 수 있다.
- RDB에는 데이터가 있지만 MongoDB 활동내역에는 영원히 반영되지 않을 수 있다.

판단
- 이벤트 유실 가능성을 설명하기 어렵기 때문에 기본안으로 사용하지 않는다.
```

```text
3. MongoDB까지 요청 흐름에서 동기 저장

장점
- MongoDB Read Model이 즉시 반영된다.

단점
- 사용자 응답 시간이 늘어난다.
- RDB와 MongoDB 중 하나만 성공하는 dual-write 문제가 생긴다.
- 실패 보상 로직이 복잡해진다.

판단
- Outbox의 목적과 맞지 않으므로 기본안에서 제외한다.
```
