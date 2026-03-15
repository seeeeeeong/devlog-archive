package com.devlog.archive.similar

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class SimilarController(
    private val similarService: SimilarService,
) {
    @PostMapping("/similar")
    fun findSimilar(@Valid @RequestBody request: SimilarRequest): ResponseEntity<SimilarResponse> {
        val result = similarService.findSimilar(request)
        return ResponseEntity.ok(result)
    }
}

data class SimilarRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val title: String,

    @field:NotBlank
    @field:Size(max = 4000)
    val content: String,

    @field:Min(1) @field:Max(10)
    val topK: Int = 5,
)

data class SimilarResponse(
    val items: List<SimilarArticleDto>,
)

data class SimilarArticleDto(
    val articleId: Long,
    val title: String,
    val company: String,
    val url: String,
    val summary: String?,
    val publishedAt: String?,
    val similarity: Double,
)
