-- Article: 조회수 집계 컬럼 추가
ALTER TABLE articles
    ADD COLUMN view_count integer not null default 0;

-- Article: 댓글수 집계 컬럼 추가
ALTER TABLE articles
    ADD COLUMN comment_count integer not null default 0;