-- HNSW index for cosine similarity search on article embeddings
-- m=16: connections per node (default 16, good for <10K rows)
-- ef_construction=64: build-time quality (higher = better recall, slower build)
CREATE INDEX idx_article_embeddings_hnsw
    ON article_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
