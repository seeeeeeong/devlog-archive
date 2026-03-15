package com.devlog.archive.article

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ArticleSimilarityRepository : Repository<ArticleEntity, Long> {

    @Query(
        value = """
            SELECT a.id, a.blog_id, a.title, a.url, a.url_hash, a.summary, a.published_at, a.crawled_at,
                   1 - (ae.embedding <=> CAST(:embedding AS vector)) AS similarity
            FROM articles a
            JOIN article_embeddings ae ON a.id = ae.article_id
            ORDER BY ae.embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findSimilar(
        @Param("embedding") embedding: String,
        @Param("limit") limit: Int,
    ): List<SimilarArticleRow>
}

interface SimilarArticleRow {
    val id: Long
    val title: String
    val url: String
    val summary: String?
    val publishedAt: LocalDateTime?
    val similarity: Double
    val blogId: Long
}
