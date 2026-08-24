-- Notification: 알림 삭제 배치(confirmed=true AND confirmed_at < 기준시각) 조회 성능을 위한 복합 인덱스
create index idx_notifications_confirmed_confirmed_at on notifications(confirmed, confirmed_at);