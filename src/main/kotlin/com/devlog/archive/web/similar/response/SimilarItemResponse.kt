package com.devlog.archive.web.similar.response

import com.devlog.archive.domain.similar.service.dto.SimilarMatch

data class SimilarItemResponse(
    val articleId: Long,
    val title: String,
    val company: String,
    val url: String,
    val summary: String?,
    val publishedAt: String?,
    val similarity: Double,
) {
    companion object {
        fun of(match: SimilarMatch): SimilarItemResponse = SimilarItemResponse(
            articleId = match.articleId,
            title = match.title,
            company = match.company,
            url = match.url,
            summary = match.summary,
            publishedAt = match.publishedAt,
            similarity = match.similarity,
        )
    }
}
