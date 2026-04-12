package com.devlog.archive.web.similar.request

import com.devlog.archive.domain.similar.service.dto.SimilarQuery
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SimilarPostRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val title: String,

    @field:NotBlank
    @field:Size(max = 4000)
    val content: String,

    @field:Min(1) @field:Max(10)
    val topK: Int = 5,
) {
    fun toQuery(): SimilarQuery = SimilarQuery(
        title = title,
        content = content,
        topK = topK,
    )
}
