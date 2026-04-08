package com.devlog.archive.web.similar

import com.devlog.archive.domain.similar.service.SimilarClickService
import com.devlog.archive.domain.similar.service.SimilarService
import com.devlog.archive.web.similar.request.SimilarClickPostRequest
import com.devlog.archive.web.similar.request.SimilarPostRequest
import com.devlog.archive.web.similar.response.SimilarClickPostResponse
import com.devlog.archive.web.similar.response.SimilarGetResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class SimilarController(
    private val similarService: SimilarService,
    private val similarClickService: SimilarClickService,
) {
    @PostMapping("/similar")
    fun findSimilar(@Valid @RequestBody request: SimilarPostRequest): SimilarGetResponse =
        SimilarGetResponse.of(similarService.findSimilar(request.toQuery()))

    @PostMapping("/similar/click")
    fun recordClick(@Valid @RequestBody request: SimilarClickPostRequest): SimilarClickPostResponse {
        similarClickService.record(request.toCommand())
        return SimilarClickPostResponse.of()
    }
}
