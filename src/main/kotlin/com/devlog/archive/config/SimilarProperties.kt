package com.devlog.archive.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "similar")
data class SimilarProperties(
    val minimumVectorSimilarity: Double = 0.55,
    val strongVectorSimilarity: Double = 0.72,
    val minimumFinalScore: Double = 0.52,
    val fallbackVectorSimilarity: Double = 0.44,
    val fallbackFinalScore: Double = 0.42,
    val lastResortVectorSimilarity: Double = 0.37,
    val minimumKeywordOverlap: Double = 0.12,
    val minimumTitleOverlap: Double = 0.2,
    val lexicalStrictScore: Double = 0.46,
    val maxPerBlog: Int = 2,
    val strict: WeightConfig = WeightConfig(),
    val fallback: FallbackWeightConfig = FallbackWeightConfig(),
    val lexical: LexicalWeightConfig = LexicalWeightConfig(),
) {
    data class WeightConfig(
        val vector: Double = 0.62,
        val titleOverlap: Double = 0.16,
        val bodyOverlap: Double = 0.08,
        val topicOverlap: Double = 0.08,
        val phraseScore: Double = 0.06,
    )

    data class FallbackWeightConfig(
        val vectorDominant: Double = 0.75,
        val lexicalDominant: Double = 0.88,
    )

    data class LexicalWeightConfig(
        val titleOverlap: Double = 0.34,
        val bodyOverlap: Double = 0.2,
        val topicOverlap: Double = 0.3,
        val phraseScore: Double = 0.16,
    )
}
