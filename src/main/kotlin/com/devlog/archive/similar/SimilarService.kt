package com.devlog.archive.similar

import com.devlog.archive.article.ArticleSimilarityRepository
import com.devlog.archive.blog.BlogCacheService
import com.devlog.archive.embedding.EmbeddingClient
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.format.DateTimeFormatter

@Service
class SimilarService(
    private val articleSimilarityRepository: ArticleSimilarityRepository,
    private val blogCacheService: BlogCacheService,
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

        val candidates = articleSimilarityRepository.findSimilar(vectorLiteral, request.topK * 3)
        log.debug("유사글 후보: {}개, 첫 번째 유사도={}", candidates.size, candidates.firstOrNull()?.similarity)

        val blogMap = blogCacheService.findAll().associate { it.id to it.company }

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
