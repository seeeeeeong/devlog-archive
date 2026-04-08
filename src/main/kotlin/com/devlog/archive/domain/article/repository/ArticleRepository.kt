package com.devlog.archive.domain.article.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import com.devlog.archive.domain.article.entity.ArticleEntity

interface ArticleRepository : JpaRepository<ArticleEntity, Long> {

    fun existsByUrlHash(urlHash: String): Boolean

    fun findAllByBlogCompany(company: String, pageable: Pageable): Page<ArticleEntity>

    @Query(
        value = "SELECT * FROM articles a WHERE NOT EXISTS (SELECT 1 FROM article_embeddings ae WHERE ae.article_id = a.id) ORDER BY a.crawled_at DESC LIMIT :limit",
        nativeQuery = true,
    )
    fun findArticlesWithoutEmbedding(limit: Int): List<ArticleEntity>

    @Query(
        value = "SELECT * FROM articles a WHERE a.topic_hints IS NULL OR a.topic_hints = '' ORDER BY a.crawled_at DESC LIMIT :limit",
        nativeQuery = true,
    )
    fun findArticlesWithoutTopicHints(limit: Int): List<ArticleEntity>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "UPDATE articles SET topic_hints = :topicHints WHERE id = :id",
        nativeQuery = true,
    )
    fun updateTopicHints(
        @Param("id") id: Long,
        @Param("topicHints") topicHints: String?,
    ): Int
}
