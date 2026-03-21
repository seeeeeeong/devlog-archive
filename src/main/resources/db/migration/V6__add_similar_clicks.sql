-- 추천 클릭 추적 테이블
CREATE TABLE similar_clicks (
    id            BIGSERIAL PRIMARY KEY,
    article_id    BIGINT NOT NULL REFERENCES articles(id),
    source_title  VARCHAR(500),
    stage         VARCHAR(20),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_similar_clicks_article_id ON similar_clicks(article_id);
CREATE INDEX idx_similar_clicks_created_at ON similar_clicks(created_at DESC);
