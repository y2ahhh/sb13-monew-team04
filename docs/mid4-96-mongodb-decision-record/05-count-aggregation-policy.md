# 카운트 집계 처리 기준

[상위 문서](./README.md) | [이전: Outbox 설계](./04-outbox-design.md) | [다음: 후속 적용 검토 흐름 및 결론](./06-final-flow-and-conclusion.md)

MongoDB Read Model은 조회 성능을 위해 역정규화될 수 있지만, RDB 원본 모델까지 바로 역정규화하지는 않는다.

예를 들어 댓글 좋아요 수를 RDB에서 `COUNT(*)`로 집계하는 구조라면, MongoDB 반영만을 위해 댓글 테이블에 `like_count` 컬럼을 바로 추가하지 않는다.

```text
RDB = 정규화된 원본 데이터 유지
MongoDB = 활동내역 조회를 위한 역정규화 Read Model
```

댓글 좋아요 수, 기사 조회수, 기사 댓글 수, 관심사 구독자 수 같은 집계값은 원본 엔티티의 `source_version`과 억지로 연결하지 않는다.

예를 들어 댓글 내용 수정은 댓글 엔티티의 version으로 순서를 판단할 수 있지만, 댓글 좋아요 수는 좋아요 테이블의 집계 결과이므로 댓글 version만으로 순서를 판단하기 어렵다.

따라서 집계값 변경 이벤트는 다음 기준으로 처리한다.

집계 이벤트의 핵심은 이벤트 payload에 들어온 과거 값을 그대로 반영하는 것이 아니라, worker가 처리하는 시점의 RDB 현재값으로 MongoDB snapshot을 수렴시키는 것이다. 따라서 중복 이벤트나 일부 순서 역전이 발생해도 마지막으로 처리된 집계 이벤트는 처리 시점의 현재 집계값을 다시 반영한다.

```text
COMMENT_LIKE_CHANGED
-> payload에는 commentId, eventId, occurredAt 같은 최소 식별 정보만 담는다.
-> worker가 이벤트 처리 시점에 RDB에서 현재 likeCount를 다시 집계한다.
-> MongoDB comment snapshot에 likeCount를 $set으로 반영한다.

ARTICLE_COMMENT_COUNT_CHANGED
-> worker가 RDB에서 현재 commentCount를 다시 집계한다.
-> MongoDB article snapshot에 commentCount를 $set으로 반영한다.

INTEREST_SUBSCRIBER_COUNT_CHANGED
-> worker가 RDB에서 현재 subscriberCount를 다시 집계한다.
-> MongoDB interest snapshot에 subscriberCount를 $set으로 반영한다.
```

이 방식은 worker가 추가 RDB 조회를 수행한다는 단점이 있지만, RDB 원본 모델을 불필요하게 역정규화하지 않고 이벤트 순서가 일부 바뀌어도 최신 집계값으로 수렴하기 쉽다.

카운트 처리 대안은 다음과 같이 남긴다.

```text
1. RDB counter 컬럼 또는 counter 테이블 추가

장점
- 카운트 조회와 버전 처리가 명확하다.
- 이벤트 payload에 확정된 카운트와 counter version을 담기 쉽다.

단점
- RDB 원본 모델이 역정규화된다.
- 카운트 정합성 관리 로직이 추가된다.

판단
- 성능 병목이 검증되기 전에는 기본안으로 두지 않는다.
```

```text
2. worker가 RDB에서 현재 집계값 재조회

장점
- RDB 정규화 구조를 유지한다.
- 중복 이벤트나 순서 역전이 있어도 현재 집계값으로 수렴하기 쉽다.

단점
- worker가 추가 RDB 조회를 수행한다.

판단
- 초기 MongoDB Read Model 설계의 기본안으로 사용한다.
```

```text
3. 이벤트 payload에 카운트 최종값 포함

장점
- worker가 RDB를 다시 조회하지 않아도 된다.

단점
- 카운트 버전 기준이 명확하지 않으면 오래된 이벤트가 최신 카운트를 덮어쓸 수 있다.

판단
- 별도 counter version이 없는 집계값에는 기본 적용하지 않는다.
```

정리하면 `source_version`은 댓글 내용, 기사 제목, 관심사 키워드처럼 원본 엔티티 자체가 변경되는 필드의 순서 판단에 사용할 수 있다.

다만 현재는 엔티티 version 필드 추가가 먼저 필요하므로 별도 검토 대상으로 둔다.

`likeCount`처럼 다른 테이블을 기준으로 집계하는 값에는 엔티티 `source_version`을 억지로 붙이지 않는다.
