package com.devlog.archive.api

import com.devlog.archive.core.EmbeddingClient
import com.devlog.archive.storage.ArticleRepository
import com.devlog.archive.storage.BlogRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.format.DateTimeFormatter

@Service
class SimilarService(
    private val articleRepository: ArticleRepository,
    private val blogRepository: BlogRepository,
    private val embeddingClient: EmbeddingClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @Cacheable(cacheNames = ["similar"], key = "#root.target.cacheKey(#request.title, #request.content, #request.topK)")
    fun findSimilar(request: SimilarRequest): SimilarResponse {
        log.debug("유사글 검색: title={}", request.title)

        val queryText = "${request.title} ${request.content.take(2000)}".trim()
        val embedding = embeddingClient.embed(queryText)
        val vectorLiteral = embedding.joinToString(",", "[", "]")

        val candidates = articleRepository.findSimilar(vectorLiteral, request.topK * 3)
        log.debug("유사글 후보: {}개, 첫 번째 유사도={}", candidates.size, candidates.firstOrNull()?.similarity)

        // 블로그 ID → 회사명 매핑
        val blogMap = blogRepository.findAll().associate { it.id to it.company }

        // 유사도 0.4 이상만 반환 (관련도 낮은 글 제외)
        val result = candidates
            .filter { it.similarity >= 0.4 }
            .mapNotNull { row ->
                val company = blogMap[row.blogId] ?: return@mapNotNull null
                SimilarArticleDto(
                    articleId = row.id,
                    title = row.title,
                    company = company,
                    url = row.url,
                    summary = row.summary,
                    publishedAt = row.publishedAt?.format(formatter),
                    similarity = row.similarity,
                )
            }
            .take(request.topK)

        return SimilarResponse(items = result)
    }

    fun cacheKey(title: String, content: String, topK: Int): String {
        val input = "$title::${content.take(2000)}::$topK"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
