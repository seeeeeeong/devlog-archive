package com.devlog.archive.domain.similar.service.dto

data class SimilarClickCommand(
    val articleId: Long,
    val sourceTitle: String?,
    val position: Short?,
    val totalResults: Short?,
    val rrfScore: Double?,
    val stage: String?,
)
