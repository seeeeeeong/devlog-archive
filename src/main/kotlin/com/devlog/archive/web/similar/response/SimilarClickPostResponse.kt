package com.devlog.archive.web.similar.response

data class SimilarClickPostResponse(
    val status: String,
) {
    companion object {
        fun of(status: String = "ok"): SimilarClickPostResponse = SimilarClickPostResponse(status = status)
    }
}
