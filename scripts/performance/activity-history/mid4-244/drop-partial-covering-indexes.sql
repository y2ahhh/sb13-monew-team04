-- MID4-244 실험 인덱스를 제거해 현재 develop 인덱스 상태로 되돌립니다.

\set ON_ERROR_STOP on
SET client_min_messages = warning;

DROP INDEX IF EXISTS idx_perf_comments_active_article_cover;
DROP INDEX IF EXISTS idx_perf_article_views_active_article_cover;
DROP INDEX IF EXISTS idx_perf_comment_likes_active_comment_cover;
DROP INDEX IF EXISTS idx_perf_subscriptions_active_interest_cover;

ANALYZE comments;
ANALYZE article_views;
ANALYZE comment_likes;
ANALYZE subscriptions;
