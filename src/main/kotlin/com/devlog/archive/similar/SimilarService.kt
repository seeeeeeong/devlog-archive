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

        val vectorCandidates = if (embedding != null) {
            val vectorLiteral = embedding.joinToString(",", "[", "]")
            articleSimilarityRepository.findSimilar(vectorLiteral, props.vectorCandidateLimit)
        } else {
            emptyList()
        }

        val ftsQuery = buildFtsQuery(request.title, normalizedTopicHints)
        val lexicalCandidates = if (ftsQuery.isNotBlank()) {
            try {
                articleSimilarityRepository.findByFullTextSearch(ftsQuery, props.ftsCandidateLimit)
            } catch (e: Exception) {
                log.debug("FTS 검색 실패, 날짜순 fallback: error={}", e.message)
                articleSimilarityRepository.findLexicalCandidates(props.ftsCandidateLimit)
            }
        } else {
            articleSimilarityRepository.findLexicalCandidates(props.ftsCandidateLimit)
        }

        if (vectorCandidates.isEmpty() && lexicalCandidates.isEmpty()) {
            recordOutcome(stage = "empty", topVector = 0.0, resultCount = 0, vectorCount = 0, lexicalCount = 0)
            return SimilarResponse(items = emptyList())
        }

        val rrfScored = computeRrfScores(vectorCandidates, lexicalCandidates)

        val blogMap = blogCacheService.findAll().associate { it.id to it.company }
        val companyCount = mutableMapOf<String, Int>()

        val result = rrfScored
            .filter { it.score >= props.minimumRrfScore }
            .filter { candidate ->
                val company = blogMap[candidate.candidate.blogId] ?: return@filter false
                val count = companyCount.getOrDefault(company, 0)
                if (count < props.maxPerBlog) {
                    companyCount[company] = count + 1
                    true
                } else {
                    false
                }
            }
            .take(request.topK)
            .map { ranked ->
                val company = blogMap[ranked.candidate.blogId]!!
                SimilarArticleDto(
                    articleId = ranked.candidate.id,
                    title = ranked.candidate.title,
                    company = company,
                    url = ranked.candidate.url,
                    summary = ranked.candidate.summary,
                    publishedAt = ranked.candidate.publishedAt?.format(formatter),
                    similarity = ranked.score,
                )
            }

        val topVector = vectorCandidates.firstOrNull()?.similarity ?: 0.0
        val stage = when {
            embedding == null && result.isNotEmpty() -> "lexical_fallback"
            result.isNotEmpty() -> "rrf"
            else -> "empty"
        }
        recordOutcome(
            stage = stage,
            topVector = topVector,
            resultCount = result.size,
            vectorCount = vectorCandidates.size,
            lexicalCount = lexicalCandidates.size,
        )
        log.info(
            "유사글 결과: title={}, topics={}, stage={}, vectorCandidates={}, lexicalCandidates={}, topVector={}, resultCount={}",
            request.title,
            normalizedTopicHints,
            stage,
            vectorCandidates.size,
            lexicalCandidates.size,
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

    private fun computeRrfScores(
        vectorCandidates: List<SimilarArticleRow>,
        lexicalCandidates: List<LexicalArticleRow>,
    ): List<RankedCandidate> {
        val k = props.rrfK
        val candidateMap = linkedMapOf<Long, SearchCandidate>()
        val vectorRank = mutableMapOf<Long, Int>()
        val ftsRank = mutableMapOf<Long, Int>()

        vectorCandidates.forEachIndexed { index, row ->
            vectorRank[row.id] = index + 1
            candidateMap[row.id] = SearchCandidate.from(row, row.similarity)
        }

        lexicalCandidates.forEachIndexed { index, row ->
            ftsRank[row.id] = index + 1
            candidateMap.putIfAbsent(row.id, SearchCandidate.from(row, null))
        }

        return candidateMap.map { (id, candidate) ->
            val vr = vectorRank[id]
            val fr = ftsRank[id]
            val score = (if (vr != null) 1.0 / (k + vr) else 0.0) +
                (if (fr != null) 1.0 / (k + fr) else 0.0)
            RankedCandidate(candidate = candidate, score = score)
        }.sortedByDescending { it.score }
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

    private fun extractKeywords(text: String, limit: Int): Set<String> {
        if (text.isBlank()) return emptySet()

        return text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}.\\s]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .asSequence()
            .map { it.trim().trimEnd('.') }
            .filter { it.length >= 2 }
            .filterNot { it in stopWords }
            .distinct()
            .take(limit)
            .toSet()
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
