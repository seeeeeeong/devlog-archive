package com.devlog.archive.domain.article.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import com.devlog.archive.domain.article.entity.ArticleEntity

interface ArticleRepository : JpaRepository<ArticleEntity, Long> {

    fun existsByUrlHash(urlHash: String): Boolean

    @Query(
        value = "SELECT * FROM articles a WHERE NOT EXISTS (SELECT 1 FROM article_embeddings ae WHERE ae.article_id = a.id) ORDER BY a.crawled_at DESC LIMIT :limit",
        nativeQuery = true,
    )
    fun findArticlesWithoutEmbedding(limit: Int): List<ArticleEntity>

}
