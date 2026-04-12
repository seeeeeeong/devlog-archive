package com.devlog.archive.domain.article.repository

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import com.devlog.archive.domain.article.entity.ArticleEntity

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

    @Query(
        value = """
            SELECT a.id, a.blog_id, a.title, a.url, a.url_hash, a.summary, a.published_at, a.crawled_at,
                   ts_rank(a.search_vector, to_tsquery('simple', :query)) AS text_rank
            FROM articles a
            WHERE a.search_vector @@ to_tsquery('simple', :query)
            ORDER BY text_rank DESC
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findByFullTextSearch(
        @Param("query") query: String,
        @Param("limit") limit: Int,
    ): List<LexicalArticleRow>

    @Query(
        value = """
            SELECT a.id, a.blog_id, a.title, a.url, a.url_hash, a.summary, a.published_at, a.crawled_at
            FROM articles a
            ORDER BY a.published_at DESC NULLS LAST, a.id DESC
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findLexicalCandidates(
        @Param("limit") limit: Int,
    ): List<LexicalArticleRow>
}

interface CandidateArticleRow {
    val id: Long
    val title: String
    val url: String
    val summary: String?
    val publishedAt: LocalDateTime?
    val blogId: Long
}

interface SimilarArticleRow : CandidateArticleRow {
    val similarity: Double
}

interface LexicalArticleRow : CandidateArticleRow
