-- MID4-133: activity history read-path indexes selected from MID4-132 RDB baseline.

-- Recent comments: user filter + latest ordering.
create index idx_comments_user_created_id
    on comments(user_id, created_at desc, id desc);

-- Recent article views: comment count subquery by article.
create index idx_comments_article
    on comments(article_id);

-- Recent liked comments: liked user filter + latest ordering.
create index idx_comment_likes_liked_by_created_id
    on comment_likes(liked_by, created_at desc, id desc);

-- Recent viewed articles: user filter + latest ordering.
-- The previous idx_article_views_user_viewed was intentionally dropped for baseline measurement.
-- This new index includes id desc to match the final activity query ordering.
create index idx_article_views_user_viewed_id
    on article_views(user_id, viewed_at desc, id desc);

-- Subscribed interests: user filter + latest ordering.
create index idx_subscriptions_user_created_id
    on subscriptions(user_id, created_at desc, id desc);
