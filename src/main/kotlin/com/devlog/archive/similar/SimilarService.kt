package com.devlog.archive.similar

import com.devlog.archive.article.ArticleSimilarityRepository
import com.devlog.archive.article.ArticleTopicHintExtractor
import com.devlog.archive.article.CandidateArticleRow
import com.devlog.archive.article.LexicalArticleRow
import com.devlog.archive.article.SimilarArticleRow
import com.devlog.archive.blog.BlogCacheService
import com.devlog.archive.embedding.EmbeddingClient
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

@Service
class SimilarService(
    private val articleSimilarityRepository: ArticleSimilarityRepository,
    private val blogCacheService: BlogCacheService,
    private val embeddingClient: EmbeddingClient,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val candidateMultiplier = 10
    private val minimumCandidateCount = 20
    private val lexicalCandidateMultiplier = 5
    private val minimumLexicalCandidateCount = 120
    private val minimumVectorSimilarity = 0.55
    private val strongVectorSimilarity = 0.72
    private val minimumKeywordOverlap = 0.12
    private val minimumTitleOverlap = 0.2
    private val minimumFinalScore = 0.52
    private val lexicalStrictScore = 0.46
    private val fallbackVectorSimilarity = 0.44
    private val fallbackFinalScore = 0.42
    private val lastResortVectorSimilarity = 0.37
    private val stopWords = setOf(
        "the", "and", "for", "with", "from", "that", "this", "into", "about", "have", "has",
        "how", "what", "when", "where", "will", "your", "post", "blog", "code", "using", "use",
        "java", "kotlin", "spring", "boot", "api", "http", "https", "www", "com", "dev", "log",
        "guide", "story", "engineering", "system", "service", "platform",
        "에서", "으로", "하다", "하는", "했다", "대한", "관련", "정리", "구현", "문제", "해결", "개발", "적용",
        "기능", "구조", "설계", "이슈", "트러블", "슈팅", "사용", "방법", "이렇게", "이유", "운영기",
    )

    @Cacheable(
        cacheNames = ["similar"],
        key = "#root.target.cacheKey(#request.title, #request.content, #request.topicHints, #request.topK)",
        unless = "#result.items.isEmpty()",
    )
    fun findSimilar(request: SimilarRequest): SimilarResponse {
        log.debug("유사글 검색: title={}", request.title)

        val normalizedTopicHints = normalizeTopicHints(request.topicHints)
        val queryText = buildQueryText(request, normalizedTopicHints)
        val embedding = try {
            embeddingClient.embed(queryText)
        } catch (e: RestClientException) {
            recordOutcome(stage = "embedding_error", topVector = 0.0, resultCount = 0, vectorCount = 0, lexicalCount = 0)
            log.warn("임베딩 호출 실패로 유사글 검색을 빈 결과로 처리합니다: title={}, error={}", request.title, e.message)
            return SimilarResponse(items = emptyList())
        } catch (e: IllegalStateException) {
            recordOutcome(stage = "embedding_error", topVector = 0.0, resultCount = 0, vectorCount = 0, lexicalCount = 0)
            log.warn("임베딩 응답 검증 실패로 유사글 검색을 빈 결과로 처리합니다: title={}, error={}", request.title, e.message)
            return SimilarResponse(items = emptyList())
        }

        val vectorLiteral = embedding.joinToString(",", "[", "]")
        val vectorCandidateLimit = max(request.topK * candidateMultiplier, minimumCandidateCount)
        val lexicalCandidateLimit = max(vectorCandidateLimit * lexicalCandidateMultiplier, minimumLexicalCandidateCount)

        val queryTitleTokens = extractKeywords(request.title, 10)
        val queryBodyTokens = extractKeywords(request.content, 28)
        val queryTopicTokens = extractKeywords(normalizedTopicHints.joinToString(" "), 16)
        val queryFocusTokens = if ((queryTitleTokens + queryTopicTokens).isEmpty()) queryBodyTokens else queryTitleTokens + queryTopicTokens
        val queryAllTokens = queryTitleTokens + queryBodyTokens + queryTopicTokens
        val queryPhrases = buildQueryPhrases(request.title, normalizedTopicHints)

        val vectorCandidates = articleSimilarityRepository.findSimilar(vectorLiteral, vectorCandidateLimit)
        val lexicalCandidates = articleSimilarityRepository.findLexicalCandidates(lexicalCandidateLimit)
        val topVector = vectorCandidates.firstOrNull()?.similarity ?: 0.0

        val mergedCandidates = mergeCandidates(vectorCandidates, lexicalCandidates)
        val blogMap = blogCacheService.findAll().associate { it.id to it.company }

        val strictResult = mergedCandidates.toRankedCandidates(
            scorer = { candidate -> scoreCandidate(candidate, queryFocusTokens, queryAllTokens, queryTopicTokens, queryPhrases) },
            minimumScore = minimumFinalScore,
            blogMap = blogMap,
            topK = request.topK,
        )

        val stageAndResult = when {
            strictResult.isNotEmpty() -> "strict" to strictResult
            else -> {
                log.info(
                    "유사글 strict 결과 없음, fallback 적용: title={}, topics={}, vectorCandidates={}, lexicalCandidates={}, topVector={}",
                    request.title,
                    normalizedTopicHints,
                    vectorCandidates.size,
                    lexicalCandidates.size,
                    topVector,
                )
                val fallbackResult = mergedCandidates.toRankedCandidates(
                    scorer = { candidate -> scoreFallbackCandidate(candidate, queryFocusTokens, queryAllTokens, queryTopicTokens, queryPhrases) },
                    minimumScore = fallbackFinalScore,
                    blogMap = blogMap,
                    topK = request.topK,
                )

                when {
                    fallbackResult.isNotEmpty() -> "fallback" to fallbackResult
                    else -> {
                        log.info(
                            "유사글 fallback도 결과 없음, last resort 적용: title={}, topics={}, vectorCandidates={}, lexicalCandidates={}, topVector={}",
                            request.title,
                            normalizedTopicHints,
                            vectorCandidates.size,
                            lexicalCandidates.size,
                            topVector,
                        )
                        val lastResortResult = mergedCandidates.toRankedCandidates(
                            scorer = { candidate -> scoreLastResortCandidate(candidate, queryFocusTokens, queryAllTokens, queryTopicTokens, queryPhrases) },
                            minimumScore = lastResortVectorSimilarity,
                            blogMap = blogMap,
                            topK = min(request.topK, 1),
                        )
                        if (lastResortResult.isNotEmpty()) "last_resort" to lastResortResult else "empty" to emptyList()
                    }
                }
            }
        }

        val (stage, result) = stageAndResult
        recordOutcome(
            stage = stage,
            topVector = topVector,
            resultCount = result.size,
            vectorCount = vectorCandidates.size,
            lexicalCount = lexicalCandidates.size,
        )
        log.info(
            "유사글 결과: title={}, topics={}, stage={}, vectorCandidates={}, lexicalCandidates={}, mergedCandidates={}, topVector={}, resultCount={}",
            request.title,
            normalizedTopicHints,
            stage,
            vectorCandidates.size,
            lexicalCandidates.size,
            mergedCandidates.size,
            topVector,
            result.size,
        )

        return SimilarResponse(items = result)
    }

    fun cacheKey(title: String, content: String, topicHints: List<String>, topK: Int): String {
        val input = "$title::${content.take(2500)}::${normalizeTopicHints(topicHints).joinToString("|")}::$topK"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun buildQueryText(request: SimilarRequest, topicHints: List<String>): String {
        return buildList {
            add(request.title)
            add(request.title)
            if (topicHints.isNotEmpty()) {
                add(topicHints.joinToString(" "))
            }
            add(request.content.take(2500))
        }.joinToString(" ").trim()
    }

    private fun normalizeTopicHints(topicHints: List<String>): List<String> {
        return topicHints.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
            .toList()
    }

    private fun scoreCandidate(
        candidate: SearchCandidate,
        queryFocusTokens: Set<String>,
        queryAllTokens: Set<String>,
        queryTopicTokens: Set<String>,
        queryPhrases: List<String>,
    ): RankedCandidate {
        val lexicalSignals = computeLexicalSignals(candidate, queryFocusTokens, queryAllTokens, queryTopicTokens, queryPhrases)
        val vectorScore = candidate.vectorSimilarity ?: 0.0
        val hasKeywordSignal = lexicalSignals.titleOverlap >= minimumTitleOverlap ||
            lexicalSignals.bodyOverlap >= minimumKeywordOverlap ||
            lexicalSignals.topicOverlap >= 0.18 ||
            lexicalSignals.phraseScore >= 0.35

        val finalScore = when {
            vectorScore >= minimumVectorSimilarity && hasKeywordSignal ->
                vectorScore * 0.62 +
                    lexicalSignals.titleOverlap * 0.16 +
                    lexicalSignals.bodyOverlap * 0.08 +
                    lexicalSignals.topicOverlap * 0.08 +
                    lexicalSignals.phraseScore * 0.06

            vectorScore >= strongVectorSimilarity ->
                vectorScore * 0.88 + lexicalSignals.lexicalScore * 0.12

            lexicalSignals.lexicalScore >= lexicalStrictScore &&
                (lexicalSignals.phraseScore >= 0.5 || lexicalSignals.topicOverlap >= 0.5) ->
                lexicalSignals.lexicalScore * 0.92

            else -> 0.0
        }

        return RankedCandidate(candidate = candidate, score = finalScore)
    }

    private fun scoreFallbackCandidate(
        candidate: SearchCandidate,
        queryFocusTokens: Set<String>,
        queryAllTokens: Set<String>,
        queryTopicTokens: Set<String>,
        queryPhrases: List<String>,
    ): RankedCandidate {
        val lexicalSignals = computeLexicalSignals(candidate, queryFocusTokens, queryAllTokens, queryTopicTokens, queryPhrases)
        val vectorScore = candidate.vectorSimilarity ?: 0.0
        val fallbackScore = max(
            vectorScore * 0.75 + lexicalSignals.lexicalScore * 0.25,
            lexicalSignals.lexicalScore * 0.88 + vectorScore * 0.12,
        )

        val isEligible = vectorScore >= fallbackVectorSimilarity ||
            lexicalSignals.lexicalScore >= 0.46 ||
            lexicalSignals.phraseScore >= 0.55 ||
            lexicalSignals.topicOverlap >= 0.34

        return RankedCandidate(candidate = candidate, score = if (isEligible) fallbackScore else 0.0)
    }

    private fun scoreLastResortCandidate(
        candidate: SearchCandidate,
        queryFocusTokens: Set<String>,
        queryAllTokens: Set<String>,
        queryTopicTokens: Set<String>,
        queryPhrases: List<String>,
    ): RankedCandidate {
        val lexicalSignals = computeLexicalSignals(candidate, queryFocusTokens, queryAllTokens, queryTopicTokens, queryPhrases)
        val vectorScore = candidate.vectorSimilarity ?: 0.0
        val score = max(vectorScore, lexicalSignals.lexicalScore * 0.75)
        return RankedCandidate(candidate = candidate, score = score)
    }

    private fun computeLexicalSignals(
        candidate: SearchCandidate,
        queryFocusTokens: Set<String>,
        queryAllTokens: Set<String>,
        queryTopicTokens: Set<String>,
        queryPhrases: List<String>,
    ): LexicalSignals {
        val titleTokens = extractKeywords(candidate.title, 14)
        val summaryTokens = extractKeywords(candidate.summary.orEmpty(), 26)
        val candidateTopicHints = candidate.topicHints.ifEmpty {
            ArticleTopicHintExtractor.extract(candidate.title, candidate.summary)
        }
        val candidateTopicTokens = extractKeywords(candidateTopicHints.joinToString(" "), 18)
        val candidateTokens = titleTokens + summaryTokens + candidateTopicTokens
        val normalizedTitle = normalizeText(candidate.title)
        val normalizedSummary = normalizeText(candidate.summary.orEmpty())
        val normalizedTopicHints = normalizeText(candidateTopicHints.joinToString(" "))
        val phraseScore = if (queryPhrases.isEmpty()) {
            0.0
        } else {
            val titleMatches = queryPhrases.count { it in normalizedTitle }
            val summaryMatches = queryPhrases.count { it in normalizedSummary }
            val topicMatches = queryPhrases.count { it in normalizedTopicHints }
            min(1.0, (titleMatches + (summaryMatches * 0.5) + topicMatches) / queryPhrases.size.toDouble())
        }

        val titleOverlap = overlapRatio(queryFocusTokens, titleTokens)
        val bodyOverlap = overlapRatio(queryAllTokens, candidateTokens)
        val topicOverlap = overlapRatio(queryTopicTokens, candidateTopicTokens.ifEmpty { candidateTokens })
        val lexicalScore = max(
            min(
                1.0,
                titleOverlap * 0.34 + bodyOverlap * 0.2 + topicOverlap * 0.3 + phraseScore * 0.16,
            ),
            min(
                1.0,
                topicOverlap * 0.62 + phraseScore * 0.18 + bodyOverlap * 0.2,
            ),
        )

        return LexicalSignals(
            titleOverlap = titleOverlap,
            bodyOverlap = bodyOverlap,
            topicOverlap = topicOverlap,
            phraseScore = phraseScore,
            lexicalScore = lexicalScore,
        )
    }

    private fun extractKeywords(text: String, limit: Int): Set<String> {
        if (text.isBlank()) {
            return emptySet()
        }

        return normalizeText(text)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 2 }
            .filterNot { it in stopWords }
            .distinct()
            .take(limit)
            .toSet()
    }

    private fun normalizeText(text: String): String {
        return text.lowercase()
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("`[^`]*`"), " ")
            .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("[*_>#]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun overlapRatio(source: Set<String>, target: Set<String>): Double {
        if (source.isEmpty() || target.isEmpty()) {
            return 0.0
        }

        val overlapCount = source.count { it in target }
        return overlapCount.toDouble() / source.size.toDouble()
    }

    private fun buildQueryPhrases(title: String, topicHints: List<String>): List<String> {
        return buildSet {
            add(normalizeText(title))
            topicHints.mapTo(this) { normalizeText(it) }
        }
            .filter { it.length >= 4 }
            .take(6)
    }

    private fun mergeCandidates(
        vectorCandidates: List<SimilarArticleRow>,
        lexicalCandidates: List<LexicalArticleRow>,
    ): List<SearchCandidate> {
        val merged = linkedMapOf<Long, SearchCandidate>()

        lexicalCandidates.forEach { row ->
            merged[row.id] = SearchCandidate.from(row, vectorSimilarity = null)
        }

        vectorCandidates.forEach { row ->
            val existing = merged[row.id]
            merged[row.id] = if (existing == null) {
                SearchCandidate.from(row, vectorSimilarity = row.similarity)
            } else {
                existing.copy(
                    title = row.title,
                    url = row.url,
                    summary = row.summary,
                    topicHints = existing.topicHints.ifEmpty {
                        ArticleTopicHintExtractor.fromStorageValue(row.topicHints)
                    },
                    publishedAt = row.publishedAt,
                    blogId = row.blogId,
                    vectorSimilarity = max(existing.vectorSimilarity ?: 0.0, row.similarity),
                )
            }
        }

        return merged.values.toList()
    }

    private fun List<SearchCandidate>.toRankedCandidates(
        scorer: (SearchCandidate) -> RankedCandidate,
        minimumScore: Double,
        blogMap: Map<Long, String>,
        topK: Int,
    ): List<SimilarArticleDto> {
        return asSequence()
            .map(scorer)
            .filter { it.score >= minimumScore }
            .sortedByDescending { it.score }
            .mapNotNull { candidate ->
                val company = blogMap[candidate.candidate.blogId] ?: return@mapNotNull null
                SimilarArticleDto(
                    articleId = candidate.candidate.id,
                    title = candidate.candidate.title,
                    company = company,
                    url = candidate.candidate.url,
                    summary = candidate.candidate.summary,
                    publishedAt = candidate.candidate.publishedAt?.format(formatter),
                    similarity = candidate.score,
                )
            }
            .take(topK)
            .toList()
    }

    private fun recordOutcome(
        stage: String,
        topVector: Double,
        resultCount: Int,
        vectorCount: Int,
        lexicalCount: Int,
    ) {
        meterRegistry.counter("similar.requests", "stage", stage).increment()
        meterRegistry.summary("similar.top_vector").record(topVector)
        meterRegistry.summary("similar.result_count").record(resultCount.toDouble())
        meterRegistry.summary("similar.vector_candidate_count").record(vectorCount.toDouble())
        meterRegistry.summary("similar.lexical_candidate_count").record(lexicalCount.toDouble())
    }
}

private data class SearchCandidate(
    val id: Long,
    val title: String,
    val url: String,
    val summary: String?,
    val topicHints: List<String>,
    val publishedAt: java.time.LocalDateTime?,
    val blogId: Long,
    val vectorSimilarity: Double?,
) {
    companion object {
        fun from(row: CandidateArticleRow, vectorSimilarity: Double?): SearchCandidate {
            return SearchCandidate(
                id = row.id,
                title = row.title,
                url = row.url,
                summary = row.summary,
                topicHints = ArticleTopicHintExtractor.fromStorageValue(row.topicHints),
                publishedAt = row.publishedAt,
                blogId = row.blogId,
                vectorSimilarity = vectorSimilarity,
            )
        }
    }
}

private data class RankedCandidate(
    val candidate: SearchCandidate,
    val score: Double,
)

private data class LexicalSignals(
    val titleOverlap: Double,
    val bodyOverlap: Double,
    val topicOverlap: Double,
    val phraseScore: Double,
    val lexicalScore: Double,
)
