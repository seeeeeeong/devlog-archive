package com.devlog.archive.domain.similar.service.dto

data class SimilarQuery(
    val title: String,
    val content: String,
    val topicHints: List<String> = emptyList(),
    val topK: Int = 5,
)
