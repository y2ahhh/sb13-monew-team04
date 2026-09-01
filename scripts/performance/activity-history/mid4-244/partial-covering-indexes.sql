-- MID4-244 SQL/API 비교를 위한 실험용 인덱스입니다.
-- 운영 Flyway migration이 아니며, 효과가 확인되기 전에는 배포 스키마에 반영하지 않습니다.
--
-- 실행 예시:
-- docker exec -i <postgres-container> psql -X -U monew -d monew -v ON_ERROR_STOP=1 \
--   < scripts/performance/activity-history/mid4-244/partial-covering-indexes.sql

\set ON_ERROR_STOP on
SET client_min_messages = warning;

CREATE INDEX IF NOT EXISTS idx_perf_comments_active_article_cover
    ON comments(article_id) INCLUDE(id)
    WHERE visibility_status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_perf_article_views_active_article_cover
    ON article_views(article_id) INCLUDE(id)
    WHERE visibility_status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_perf_comment_likes_active_comment_cover
    ON comment_likes(comment_id) INCLUDE(id)
    WHERE visibility_status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_perf_subscriptions_active_interest_cover
    ON subscriptions(interest_id) INCLUDE(id)
    WHERE visibility_status = 'ACTIVE';

ANALYZE comments;
ANALYZE article_views;
ANALYZE comment_likes;
ANALYZE subscriptions;

SELECT
    indexrelname AS index_name,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
WHERE indexrelname IN (
    'idx_perf_comments_active_article_cover',
    'idx_perf_article_views_active_article_cover',
    'idx_perf_comment_likes_active_comment_cover',
    'idx_perf_subscriptions_active_interest_cover'
)
ORDER BY indexrelname;
