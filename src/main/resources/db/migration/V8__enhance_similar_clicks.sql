-- Enhance similar_clicks for analytics: position tracking and RRF score
ALTER TABLE similar_clicks
    ADD COLUMN position      SMALLINT,
    ADD COLUMN total_results SMALLINT,
    ADD COLUMN rrf_score     DOUBLE PRECISION;

CREATE INDEX idx_similar_clicks_source_title ON similar_clicks(source_title);
