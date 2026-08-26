# 이벤트 핸들러 대상

[상위 문서](./README.md) | [이전: MongoDB 저장 모델](./02-mongodb-storage-model.md) | [다음: Outbox 설계](./04-outbox-design.md)

MongoDB Read Model을 적용하면 RDB 원본 데이터의 변경을 MongoDB 조회 모델에 반영하는 이벤트 핸들러가 필요하다.

이벤트 핸들러는 MongoDB를 원본처럼 다루기 위한 것이 아니라, RDB 원본 변경을 조회 최적화용 모델에 반영하기 위한 projection 역할을 한다.

아래 이벤트 핸들러 대상은 4개 활동내역 조회 기능 전체에 대한 후보 설계다. 후속 구현은 성능 검증 후 MongoDB 적용 후보로 결정된 기능에 필요한 이벤트부터 시작한다.

## 공통 삭제 이벤트

논리삭제 개념은 사용자, 기사, 댓글에 둔다.

논리삭제 이벤트는 기존에 `visible=true`인 activity만 숨김 처리한다. 이미 `CANCELED`, `UNSUBSCRIBED`, `TARGET_DELETED`, `USER_DELETED`로 숨겨진 activity의 `status`는 덮어쓰지 않는다.

```text
사용자 논리삭제 또는 탈퇴
-> userId=deletedUserId, visible=true인 activity visible=false, status=USER_DELETED 처리

사용자 물리삭제
-> userId=deletedUserId인 activity_histories 문서 제거

댓글 논리삭제
-> targetType=COMMENT, targetId=commentId, visible=true인 activity visible=false, status=TARGET_DELETED 처리
-> comment snapshot visible=false 처리

댓글 물리삭제
-> comment snapshot 제거
-> targetType=COMMENT, targetId=commentId인 activity_histories 문서 제거

기사 논리삭제
-> targetType=ARTICLE, targetId=articleId, visible=true인 activity visible=false, status=TARGET_DELETED 처리
-> targetType=COMMENT, parentTargetType=ARTICLE, parentTargetId=articleId, visible=true인 activity visible=false, status=TARGET_DELETED 처리
-> article snapshot visible=false 처리

기사 물리삭제
-> article snapshot 제거
-> targetType=ARTICLE, targetId=articleId인 activity_histories 문서 제거
-> targetType=COMMENT, parentTargetType=ARTICLE, parentTargetId=articleId인 activity_histories 문서 제거
```

### 구독 중인 관심사

```text
관심사 구독
-> 사용자별 구독 관심사 활동 생성 또는 visible=true 처리

구독 해제
-> 해당 사용자의 구독 관심사 활동 visible=false 또는 status=UNSUBSCRIBED 처리

관심사 키워드 추가 또는 삭제
-> 관심사 snapshot의 keywords 갱신

관심사 비노출 또는 제거
-> 해당 interestId를 참조하는 구독 관심사 활동 visible=false 처리

구독자 수 변경
-> 관심사 snapshot의 subscriberCount 갱신 필요 신호로 처리
```

### 최근 작성 댓글

```text
댓글 작성
-> 작성 댓글 활동 생성

댓글 수정
-> 댓글 snapshot의 content, updatedAt 등 표시 데이터 갱신
-> 활동 정렬 기준은 댓글 작성 시각을 유지

댓글 논리삭제
-> 해당 commentId를 참조하는 visible=true 작성 댓글 활동 visible=false, status=TARGET_DELETED 처리

댓글 물리삭제
-> 해당 commentId를 참조하는 작성 댓글 activity 제거

기사 논리삭제
-> parentTargetType=ARTICLE, parentTargetId=articleId인 visible=true 작성 댓글 활동 visible=false, status=TARGET_DELETED 처리

기사 물리삭제
-> parentTargetType=ARTICLE, parentTargetId=articleId인 작성 댓글 activity 제거

댓글 좋아요 수 변경
-> 댓글 snapshot의 likeCount 갱신 필요 신호로 처리
```

### 최근 좋아요한 댓글

```text
댓글 좋아요
-> 좋아요 댓글 활동 생성 또는 visible=true 처리

좋아요 취소
-> 해당 사용자의 좋아요 댓글 활동 visible=false 또는 status=CANCELED 처리

댓글 수정
-> 댓글 snapshot의 content, updatedAt 등 표시 데이터 갱신

댓글 논리삭제
-> 해당 commentId를 참조하는 visible=true 좋아요 댓글 활동 visible=false, status=TARGET_DELETED 처리

댓글 물리삭제
-> 해당 commentId를 참조하는 좋아요 댓글 activity 제거

기사 논리삭제
-> parentTargetType=ARTICLE, parentTargetId=articleId인 visible=true 좋아요 댓글 활동 visible=false, status=TARGET_DELETED 처리

기사 물리삭제
-> parentTargetType=ARTICLE, parentTargetId=articleId인 좋아요 댓글 activity 제거

댓글 좋아요 수 변경
-> 댓글 snapshot의 likeCount 갱신 필요 신호로 처리
```

### 최근 조회 기사

```text
기사 조회
-> 최근 본 뉴스 활동 생성 또는 upsert
-> 같은 사용자가 같은 기사를 다시 조회하면 occurredAt을 최신화

기사 수정
-> 기사 snapshot의 title, summary, source, date 등 표시 데이터 갱신

기사 논리삭제
-> 해당 articleId를 참조하는 visible=true 최근 본 뉴스 활동 visible=false, status=TARGET_DELETED 처리

기사 물리삭제
-> 해당 articleId를 참조하는 최근 본 뉴스 activity 제거

조회수 변경
-> 기사 snapshot의 viewCount 갱신 필요 신호로 처리

댓글 작성 또는 삭제
-> 기사 snapshot의 commentCount 갱신 필요 신호로 처리
```
