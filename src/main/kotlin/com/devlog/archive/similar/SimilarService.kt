package com.devlog.archive.similar

import com.devlog.archive.article.ArticleSimilarityRepository
import com.devlog.archive.article.ArticleTopicHintExtractor
import com.devlog.archive.article.CandidateArticleRow
import com.devlog.archive.article.LexicalArticleRow
import com.devlog.archive.article.SimilarArticleRow
import com.devlog.archive.blog.BlogCacheService
import com.devlog.archive.common.StopWords
import com.devlog.archive.config.SimilarProperties
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
    private val props: SimilarProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val candidateMultiplier = 10
    private val minimumCandidateCount = 50
    private val stopWords = StopWords.set

    @Cacheable(
        cacheNames = ["similar"],
        key = "#root.target.cacheKey(#request.title, #request.content, #request.topicHints, #request.topK)",
    )
    fun findSimilar(request: SimilarRequest): SimilarResponse {
        log.debug("유사글 검색: title={}", request.title)

        val normalizedTopicHints = normalizeTopicHints(request.topicHints, request.title, request.content)
        val queryText = buildQueryText(request, normalizedTopicHints)
        val embedding = try {
            embeddingClient.embed(queryText)
        } catch (e: RestClientException) {
            log.warn("임베딩 호출 실패, lexical fallback 적용: title={}, error={}", request.title, e.message)
            null
        } catch (e: IllegalStateException) {
            log.warn("임베딩 응답 검증 실패, lexical fallback 적용: title={}, error={}", request.title, e.message)
            null
        }

        val vectorCandidateLimit = max(request.topK * candidateMultiplier, minimumCandidateCount)

        val queryTitleTokens = extractKeywords(request.title, 10)
        val queryBodyTokens = extractKeywords(request.content, 28)
        val queryTopicTokens = extractKeywords(normalizedTopicHints.joinToString(" "), 16)
        val queryFocusTokens = if ((queryTitleTokens + queryTopicTokens).isEmpty()) queryBodyTokens else queryTitleTokens + queryTopicTokens
        val queryAllTokens = queryTitleTokens + queryBodyTokens + queryTopicTokens
        val queryPhrases = buildQueryPhrases(request.title, normalizedTopicHints)

        val vectorCandidates = if (embedding != null) {
            val vectorLiteral = embedding.joinToString(",", "[", "]")
            articleSimilarityRepository.findSimilar(vectorLiteral, vectorCandidateLimit)
        } else {
            emptyList()
        }

        val ftsQuery = buildFtsQuery(request.title, normalizedTopicHints)
        val lexicalCandidates = if (ftsQuery.isNotBlank()) {
            try {
                articleSimilarityRepository.findByFullTextSearch(ftsQuery, vectorCandidateLimit)
            } catch (e: Exception) {
                log.debug("FTS 검색 실패, 날짜순 fallback: error={}", e.message)
                articleSimilarityRepository.findLexicalCandidates(vectorCandidateLimit)
            }
        } else {
            articleSimilarityRepository.findLexicalCandidates(vectorCandidateLimit)
        }

        val topVector = vectorCandidates.firstOrNull()?.similarity ?: 0.0

        val mergedCandidates = mergeCandidates(vectorCandidates, lexicalCandidates)

        if (embedding == null && mergedCandidates.isEmpty()) {
            recordOutcome(stage = "embedding_error", topVector = 0.0, resultCount = 0, vectorCount = 0, lexicalCount = 0)
            return SimilarResponse(items = emptyList())
        }
        val blogMap = blogCacheService.findAll().associate { it.id to it.company }

        val strictResult = mergedCandidates.toRankedCandidates(
            scorer = { candidate -> scoreCandidate(candidate, queryFocusTokens, queryAllTokens, queryTopicTokens, queryPhrases) },
            minimumScore = props.minimumFinalScore,
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
                    minimumScore = props.fallbackFinalScore,
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
                            minimumScore = props.lastResortVectorSimilarity,
                            blogMap = blogMap,
                            topK = min(request.topK, 1),
                        )
                        if (lastResortResult.isNotEmpty()) "last_resort" to lastResortResult else "empty" to emptyList()
                    }
                }
            }
        }

        val (rawStage, result) = stageAndResult
        val stage = if (embedding == null && result.isNotEmpty()) "lexical_fallback" else rawStage
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
        val input = "$title::${content.take(2500)}::${normalizeTopicHints(topicHints, title, content).joinToString("|")}::$topK"
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
            add(cleanForEmbedding(request.content).take(2500))
        }.joinToString(" ").trim()
    }

    private fun cleanForEmbedding(text: String): String {
        return text
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("`[^`]*`"), " ")
            .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("[*_>#]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildFtsQuery(title: String, topicHints: List<String>): String {
        val tokens = buildSet {
            addAll(extractKeywords(title, 6))
            topicHints.forEach { addAll(extractKeywords(it, 4)) }
        }
        return tokens.take(10).joinToString(" | ")
    }

    private fun normalizeTopicHints(topicHints: List<String>, title: String = "", content: String = ""): List<String> {
        val provided = topicHints.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
            .toList()

        if (provided.isNotEmpty()) return provided

        return ArticleTopicHintExtractor.extract(title, content.take(2500))
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
        val hasKeywordSignal = lexicalSignals.titleOverlap >= props.minimumTitleOverlap ||
            lexicalSignals.bodyOverlap >= props.minimumKeywordOverlap ||
            lexicalSignals.topicOverlap >= props.minimumTitleOverlap ||
            lexicalSignals.phraseScore >= 0.35

        val w = props.strict
        val sv = props.strongVector
        val lo = props.lexicalOnly
        val finalScore = when {
            vectorScore >= props.minimumVectorSimilarity && hasKeywordSignal ->
                vectorScore * w.vector +
                    lexicalSignals.titleOverlap * w.titleOverlap +
                    lexicalSignals.bodyOverlap * w.bodyOverlap +
                    lexicalSignals.topicOverlap * w.topicOverlap +
                    lexicalSignals.phraseScore * w.phraseScore

            vectorScore >= props.strongVectorSimilarity ->
                vectorScore * sv.vectorWeight + lexicalSignals.lexicalScore * sv.lexicalWeight

            lexicalSignals.lexicalScore >= props.lexicalStrictScore &&
                (lexicalSignals.phraseScore >= lo.minimumPhraseScore || lexicalSignals.topicOverlap >= lo.minimumTopicOverlap) ->
                lexicalSignals.lexicalScore * lo.multiplier

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
        val fw = props.fallback
        val fallbackScore = max(
            vectorScore * fw.vectorDominant + lexicalSignals.lexicalScore * (1.0 - fw.vectorDominant),
            lexicalSignals.lexicalScore * fw.lexicalDominant + vectorScore * (1.0 - fw.lexicalDominant),
        )

        val fe = props.fallbackEligibility
        val isEligible = vectorScore >= props.fallbackVectorSimilarity ||
            lexicalSignals.lexicalScore >= fe.minimumLexicalScore ||
            lexicalSignals.phraseScore >= fe.minimumPhraseScore ||
            lexicalSignals.topicOverlap >= fe.minimumTopicOverlap

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
        val score = max(vectorScore, lexicalSignals.lexicalScore * props.lastResortLexicalWeight)
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
        val lw = props.lexical
        val lexicalScore = max(
            min(
                1.0,
                titleOverlap * lw.titleOverlap + bodyOverlap * lw.bodyOverlap + topicOverlap * lw.topicOverlap + phraseScore * lw.phraseScore,
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
            .split(Regex("[^\\p{L}\\p{N}.]+"))
            .asSequence()
            .map { it.trim().trimEnd('.') }
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

        val intersectionCount = source.count { it in target }
        return intersectionCount.toDouble() / max(source.size, target.size).toDouble()
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
        val companyCount = mutableMapOf<String, Int>()
        val maxPerCompany = props.maxPerBlog

        return asSequence()
            .map(scorer)
            .filter { it.score >= minimumScore }
            .sortedByDescending { it.score }
            .filter { candidate ->
                val company = blogMap[candidate.candidate.blogId] ?: return@filter false
                val count = companyCount.getOrDefault(company, 0)
                if (count < maxPerCompany) {
                    companyCount[company] = count + 1
                    true
                } else {
                    false
                }
            }
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
