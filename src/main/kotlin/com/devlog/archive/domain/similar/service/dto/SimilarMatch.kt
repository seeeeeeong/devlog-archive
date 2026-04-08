package com.devlog.archive.domain.similar.service.dto

data class SimilarMatch(
    val articleId: Long,
    val title: String,
    val company: String,
    val url: String,
    val summary: String?,
    val publishedAt: String?,
    val similarity: Double,
)
