package com.devlog.archive.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "similar")
data class SimilarProperties(
    val rrfK: Int = 60,
    val minimumRrfScore: Double = 0.015,
    val maxPerBlog: Int = 2,
    val vectorCandidateLimit: Int = 50,
    val ftsCandidateLimit: Int = 50,
)
