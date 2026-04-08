package com.devlog.archive.web.similar.request

import com.devlog.archive.domain.similar.service.dto.SimilarClickCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class SimilarClickPostRequest(
    @field:Min(1)
    val articleId: Long,

    @field:Size(max = 500)
    val sourceTitle: String? = null,

    @field:Min(1) @field:Max(10)
    val position: Int? = null,

    @field:Min(1) @field:Max(10)
    val totalResults: Int? = null,

    val rrfScore: Double? = null,

    @field:Size(max = 20)
    val stage: String? = null,
) {
    fun toCommand(): SimilarClickCommand = SimilarClickCommand(
        articleId = articleId,
        sourceTitle = sourceTitle,
        position = position?.toShort(),
        totalResults = totalResults?.toShort(),
        rrfScore = rrfScore,
        stage = stage,
    )
}
