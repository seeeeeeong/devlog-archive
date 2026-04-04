-- Remove unused source_article_id column and add source_title index
DROP INDEX IF EXISTS idx_similar_clicks_source_article;
ALTER TABLE similar_clicks DROP COLUMN IF EXISTS source_article_id;

CREATE INDEX idx_similar_clicks_source_title ON similar_clicks(source_title);
