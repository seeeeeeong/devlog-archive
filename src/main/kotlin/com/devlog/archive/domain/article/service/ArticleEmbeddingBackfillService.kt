package com.devlog.archive.domain.article.service

import com.devlog.archive.domain.article.repository.ArticleRepository
import com.devlog.archive.domain.article.service.dto.ArticleEmbeddingBackfillResult
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service

@Service
class ArticleEmbeddingBackfillService(
    private val articleRepository: ArticleRepository,
    private val articleEmbeddingBackfillRowService: ArticleEmbeddingBackfillRowService,
    private val cacheManager: CacheManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun backfillAll(): ArticleEmbeddingBackfillResult {
        val articles = articleRepository.findAll()
        var success = 0
        var failed = 0

        articles.forEach { article ->
            try {
                articleEmbeddingBackfillRowService.process(article)
                success++
            } catch (e: Exception) {
                failed++
                log.warn("backfill 실패: articleId={}, error={}", article.id, e.message)
            }
        }

        cacheManager.getCache("similar")?.clear()
        log.info("backfill 완료: success={}, failed={}, similar 캐시 초기화", success, failed)
        return ArticleEmbeddingBackfillResult(success = success, failed = failed, total = articles.size)
    }
}
