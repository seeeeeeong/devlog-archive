package com.devlog.archive.domain.article.service.dto

data class ArticleEmbeddingBackfillResult(
    val success: Int,
    val failed: Int,
    val total: Int,
)
