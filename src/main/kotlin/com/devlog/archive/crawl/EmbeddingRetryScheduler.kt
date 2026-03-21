package com.devlog.archive.crawl

import com.devlog.archive.article.ArticleEmbeddingRepository
import com.devlog.archive.article.ArticleRepository
import com.devlog.archive.article.ArticleTopicHintExtractor
import com.devlog.archive.embedding.EmbeddingClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class EmbeddingRetryScheduler(
    private val articleRepository: ArticleRepository,
    private val embeddingClient: EmbeddingClient,
    private val articleEmbeddingRepository: ArticleEmbeddingRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)  // 시작 1분 후, 이후 1시간마다
    fun retryMissingEmbeddings() {
        val articles = articleRepository.findArticlesWithoutEmbedding(50)
        if (articles.isEmpty()) return

        log.info("임베딩 누락 기사 재시도: {}개", articles.size)
        var successCount = 0

        articles.forEach { article ->
            try {
                val topicHints = ArticleTopicHintExtractor.fromStorageValue(article.topicHints)
                    .ifEmpty { ArticleTopicHintExtractor.extract(article.title, article.summary) }
                val text = ArticleTopicHintExtractor.buildEmbeddingText(article.title, article.summary, topicHints)
                val vector = embeddingClient.embed(text)
                articleEmbeddingRepository.save(article.id, vector)
                successCount++
            } catch (e: Exception) {
                log.warn("임베딩 재시도 실패: articleId={}, error={}", article.id, e.message)
            }
        }

        log.info("임베딩 재시도 완료: 성공={}/{}", successCount, articles.size)
    }
}
