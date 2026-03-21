-- full-text search용 tsvector 컬럼 추가
ALTER TABLE articles ADD COLUMN search_vector tsvector;

-- 기존 데이터 백필
UPDATE articles SET search_vector =
    setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(topic_hints, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(summary, '')), 'C');

-- GIN 인덱스
CREATE INDEX idx_articles_search_vector ON articles USING GIN(search_vector);

-- INSERT/UPDATE 시 자동 갱신 트리거
CREATE OR REPLACE FUNCTION articles_search_vector_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('simple', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(NEW.topic_hints, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(NEW.summary, '')), 'C');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_articles_search_vector
    BEFORE INSERT OR UPDATE ON articles
    FOR EACH ROW
    EXECUTE FUNCTION articles_search_vector_trigger();
