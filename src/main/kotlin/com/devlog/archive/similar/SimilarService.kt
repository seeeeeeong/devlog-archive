package com.devlog.archive.similar

import com.devlog.archive.article.ArticleSimilarityRepository
import com.devlog.archive.article.SimilarArticleRow
import com.devlog.archive.blog.BlogCacheService
import com.devlog.archive.embedding.EmbeddingClient
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
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val candidateMultiplier = 10
    private val minimumCandidateCount = 20
    private val minimumVectorSimilarity = 0.55
    private val strongVectorSimilarity = 0.72
    private val minimumKeywordOverlap = 0.12
    private val minimumTitleOverlap = 0.2
    private val minimumFinalScore = 0.52
    private val fallbackVectorSimilarity = 0.44
    private val fallbackFinalScore = 0.42
    private val lastResortVectorSimilarity = 0.37
    private val stopWords = setOf(
        "the", "and", "for", "with", "from", "that", "this", "into", "about", "have", "has",
        "how", "what", "when", "where", "will", "your", "post", "blog", "code", "using", "use",
        "java", "kotlin", "spring", "boot", "api", "http", "https", "www", "com", "dev", "log",
        "에서", "으로", "하다", "하는", "했다", "대한", "관련", "정리", "구현", "문제", "해결", "개발", "적용",
        "기능", "구조", "설계", "이슈", "트러블", "슈팅", "사용", "방법",
    )

    @Cacheable(
        cacheNames = ["similar"],
        key = "#root.target.cacheKey(#request.title, #request.content, #request.topK)",
        unless = "#result.items.isEmpty()",
    )
    fun findSimilar(request: SimilarRequest): SimilarResponse {
        log.debug("유사글 검색: title={}", request.title)

        val queryText = "${request.title} ${request.title} ${request.content.take(2000)}".trim()
        val embedding = try {
            embeddingClient.embed(queryText)
        } catch (e: RestClientException) {
            log.warn("임베딩 호출 실패로 유사글 검색을 빈 결과로 처리합니다: title={}, error={}", request.title, e.message)
            return SimilarResponse(items = emptyList())
        } catch (e: IllegalStateException) {
            log.warn("임베딩 응답 검증 실패로 유사글 검색을 빈 결과로 처리합니다: title={}, error={}", request.title, e.message)
            return SimilarResponse(items = emptyList())
        }
        val vectorLiteral = embedding.joinToString(",", "[", "]")
        val candidateLimit = max(request.topK * candidateMultiplier, minimumCandidateCount)
        val queryTitleTokens = extractKeywords(request.title, 8)
        val queryBodyTokens = extractKeywords(request.content, 20)
        val queryAllTokens = queryTitleTokens + queryBodyTokens

        val candidates = articleSimilarityRepository.findSimilar(vectorLiteral, candidateLimit)
        log.debug("유사글 후보: {}개, 첫 번째 유사도={}", candidates.size, candidates.firstOrNull()?.similarity)

        val blogMap = blogCacheService.findAll().associate { it.id to it.company }

        val strictResult = candidates.toRankedCandidates(
            scorer = { row -> scoreCandidate(row, queryTitleTokens, queryAllTokens) },
            minimumScore = minimumFinalScore,
            blogMap = blogMap,
            topK = request.topK,
        )

        val result = if (strictResult.isNotEmpty()) {
            strictResult
        } else {
            log.info(
                "유사글 strict 결과 없음, fallback 적용: title={}, candidates={}, topVector={}",
                request.title,
                candidates.size,
                candidates.firstOrNull()?.similarity,
            )
            val fallbackResult = candidates.toRankedCandidates(
                scorer = { row -> scoreFallbackCandidate(row, queryTitleTokens, queryAllTokens) },
                minimumScore = fallbackFinalScore,
                blogMap = blogMap,
                topK = request.topK,
            )

            if (fallbackResult.isNotEmpty()) {
                fallbackResult
            } else {
                log.info(
                    "유사글 fallback도 결과 없음, last resort 적용: title={}, candidates={}, topVector={}",
                    request.title,
                    candidates.size,
                    candidates.firstOrNull()?.similarity,
                )
                candidates.toRankedCandidates(
                    scorer = { row -> RankedCandidate(row = row, score = row.similarity) },
                    minimumScore = lastResortVectorSimilarity,
                    blogMap = blogMap,
                    topK = min(request.topK, 1),
                )
            }
        }

        return SimilarResponse(items = result)
    }

    fun cacheKey(title: String, content: String, topK: Int): String {
        val input = "$title::${content.take(2000)}::$topK"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun scoreCandidate(
        row: SimilarArticleRow,
        queryTitleTokens: Set<String>,
        queryAllTokens: Set<String>,
    ): RankedCandidate {
        val vectorScore = row.similarity
        if (vectorScore < minimumVectorSimilarity) {
            return RankedCandidate(row = row, score = 0.0)
        }

        val titleTokens = extractKeywords(row.title, 12)
        val summaryTokens = extractKeywords(row.summary.orEmpty(), 20)
        val titleOverlap = overlapRatio(queryTitleTokens, titleTokens)
        val bodyOverlap = overlapRatio(queryAllTokens, titleTokens + summaryTokens)
        val hasKeywordSignal = titleOverlap >= minimumTitleOverlap || bodyOverlap >= minimumKeywordOverlap

        val finalScore = when {
            hasKeywordSignal -> vectorScore * 0.72 + titleOverlap * 0.20 + bodyOverlap * 0.08
            vectorScore >= strongVectorSimilarity -> vectorScore * 0.92
            else -> 0.0
        }

        return RankedCandidate(row = row, score = finalScore)
    }

    private fun scoreFallbackCandidate(
        row: SimilarArticleRow,
        queryTitleTokens: Set<String>,
        queryAllTokens: Set<String>,
    ): RankedCandidate {
        val vectorScore = row.similarity
        if (vectorScore < fallbackVectorSimilarity) {
            return RankedCandidate(row = row, score = 0.0)
        }

        val titleTokens = extractKeywords(row.title, 12)
        val summaryTokens = extractKeywords(row.summary.orEmpty(), 20)
        val titleOverlap = overlapRatio(queryTitleTokens, titleTokens)
        val bodyOverlap = overlapRatio(queryAllTokens, titleTokens + summaryTokens)
        val fallbackScore = vectorScore * 0.85 + titleOverlap * 0.10 + bodyOverlap * 0.05

        return RankedCandidate(row = row, score = fallbackScore)
    }

    private fun extractKeywords(text: String, limit: Int): Set<String> {
        if (text.isBlank()) {
            return emptySet()
        }

        return text.lowercase()
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("`[^`]*`"), " ")
            .replace(Regex("https?://\\S+"), " ")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 2 }
            .filterNot { it in stopWords }
            .distinct()
            .take(limit)
            .toSet()
    }

    private fun overlapRatio(source: Set<String>, target: Set<String>): Double {
        if (source.isEmpty() || target.isEmpty()) {
            return 0.0
        }

        val overlapCount = source.count { it in target }
        return overlapCount.toDouble() / source.size.toDouble()
    }

    private fun List<SimilarArticleRow>.toRankedCandidates(
        scorer: (SimilarArticleRow) -> RankedCandidate,
        minimumScore: Double,
        blogMap: Map<Long, String>,
        topK: Int,
    ): List<SimilarArticleDto> {
        return asSequence()
            .map(scorer)
            .filter { it.score >= minimumScore }
            .sortedByDescending { it.score }
            .mapNotNull { candidate ->
                val company = blogMap[candidate.row.blogId] ?: return@mapNotNull null
                SimilarArticleDto(
                    articleId = candidate.row.id,
                    title = candidate.row.title,
                    company = company,
                    url = candidate.row.url,
                    summary = candidate.row.summary,
                    publishedAt = candidate.row.publishedAt?.format(formatter),
                    similarity = candidate.score,
                )
            }
            .take(topK)
            .toList()
    }
}

private data class RankedCandidate(
    val row: SimilarArticleRow,
    val score: Double,
)
