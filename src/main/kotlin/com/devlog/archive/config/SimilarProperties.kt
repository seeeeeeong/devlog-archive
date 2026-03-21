package com.devlog.archive.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "similar")
data class SimilarProperties(
    val minimumVectorSimilarity: Double = 0.45,
    val strongVectorSimilarity: Double = 0.72,
    val minimumFinalScore: Double = 0.52,
    val fallbackVectorSimilarity: Double = 0.40,
    val fallbackFinalScore: Double = 0.38,
    val lastResortVectorSimilarity: Double = 0.42,
    val minimumKeywordOverlap: Double = 0.12,
    val minimumTitleOverlap: Double = 0.2,
    val lexicalStrictScore: Double = 0.46,
    val maxPerBlog: Int = 2,
    val strict: WeightConfig = WeightConfig(),
    val fallback: FallbackWeightConfig = FallbackWeightConfig(),
    val lexical: LexicalWeightConfig = LexicalWeightConfig(),
    val strongVector: StrongVectorConfig = StrongVectorConfig(),
    val lexicalOnly: LexicalOnlyConfig = LexicalOnlyConfig(),
    val fallbackEligibility: FallbackEligibilityConfig = FallbackEligibilityConfig(),
    val lastResortLexicalWeight: Double = 0.75,
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

    data class StrongVectorConfig(
        val vectorWeight: Double = 0.88,
        val lexicalWeight: Double = 0.12,
    )

    data class LexicalOnlyConfig(
        val multiplier: Double = 0.92,
        val minimumPhraseScore: Double = 0.5,
        val minimumTopicOverlap: Double = 0.5,
    )

    data class FallbackEligibilityConfig(
        val minimumLexicalScore: Double = 0.30,
        val minimumPhraseScore: Double = 0.40,
        val minimumTopicOverlap: Double = 0.25,
    )
}
