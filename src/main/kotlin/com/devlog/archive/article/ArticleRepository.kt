package com.devlog.archive.article

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ArticleRepository : JpaRepository<ArticleEntity, Long> {

    fun existsByUrlHash(urlHash: String): Boolean

    fun findAllByBlogCompany(company: String, pageable: Pageable): Page<ArticleEntity>

    @Query(
        value = "SELECT * FROM articles a WHERE NOT EXISTS (SELECT 1 FROM article_embeddings ae WHERE ae.article_id = a.id) LIMIT :limit",
        nativeQuery = true,
    )
    fun findArticlesWithoutEmbedding(limit: Int): List<ArticleEntity>
}
