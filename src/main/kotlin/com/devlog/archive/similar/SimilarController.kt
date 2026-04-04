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
    private val similarClickRepository: SimilarClickRepository,
) {
    @PostMapping("/similar")
    fun findSimilar(@Valid @RequestBody request: SimilarRequest): ResponseEntity<SimilarResponse> {
        val result = similarService.findSimilar(request)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/similar/click")
    fun recordClick(@Valid @RequestBody request: SimilarClickRequest): ResponseEntity<Map<String, String>> {
        similarClickRepository.save(
            SimilarClickEntity(
                articleId = request.articleId,
                sourceTitle = request.sourceTitle,
                position = request.position?.toShort(),
                totalResults = request.totalResults?.toShort(),
                rrfScore = request.rrfScore,
                stage = request.stage,
            )
        )
        return ResponseEntity.ok(mapOf("status" to "ok"))
    }
}

data class SimilarRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val title: String,

    @field:NotBlank
    @field:Size(max = 4000)
    val content: String,

    @field:Size(max = 10)
    val topicHints: List<@Size(max = 60) String> = emptyList(),

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

data class SimilarClickRequest(
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
)
