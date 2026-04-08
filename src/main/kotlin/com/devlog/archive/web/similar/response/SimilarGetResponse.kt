package com.devlog.archive.web.similar.response

import com.devlog.archive.domain.similar.service.dto.SimilarMatch

data class SimilarGetResponse(
    val items: List<SimilarItemResponse>,
) {
    companion object {
        fun of(matches: List<SimilarMatch>): SimilarGetResponse =
            SimilarGetResponse(items = matches.map(SimilarItemResponse::of))
    }
}
