-- topic_hints 컬럼 제거 전에 trigger 함수를 먼저 갱신
CREATE OR REPLACE FUNCTION articles_search_vector_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('simple', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(NEW.summary, '')), 'C');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE articles DROP COLUMN IF EXISTS topic_hints;
