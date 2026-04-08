package com.devlog.archive.domain.article.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ArticleEmbeddingRepository(private val jdbcTemplate: JdbcTemplate) {
    fun save(articleId: Long, vector: List<Double>) {
        val vectorLiteral = vector.joinToString(",", "[", "]")
        jdbcTemplate.update(
            "INSERT INTO article_embeddings (article_id, embedding) VALUES (?, ?::vector)",
            articleId,
            vectorLiteral,
        )
    }

    fun upsert(articleId: Long, vector: List<Double>) {
        val vectorLiteral = vector.joinToString(",", "[", "]")
        jdbcTemplate.update(
            """
            INSERT INTO article_embeddings (article_id, embedding) VALUES (?, ?::vector)
            ON CONFLICT (article_id) DO UPDATE SET embedding = EXCLUDED.embedding
            """.trimIndent(),
            articleId,
            vectorLiteral,
        )
    }
}
