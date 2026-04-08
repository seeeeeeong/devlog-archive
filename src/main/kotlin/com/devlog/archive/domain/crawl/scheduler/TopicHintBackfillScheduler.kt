package com.devlog.archive.domain.crawl.scheduler

import com.devlog.archive.domain.article.repository.ArticleRepository
import com.devlog.archive.domain.article.service.ArticleTopicHintExtractor
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TopicHintBackfillScheduler(
    private val articleRepository: ArticleRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 90_000)
    @Transactional
    fun backfillTopicHints() {
        val articles = articleRepository.findArticlesWithoutTopicHints(100)
        if (articles.isEmpty()) return

        var updatedCount = 0
        articles.forEach { article ->
            val topicHints = ArticleTopicHintExtractor.toStorageValue(
                ArticleTopicHintExtractor.extract(article.title, article.summary)
            )
            articleRepository.updateTopicHints(article.id, topicHints)
            updatedCount++
        }

        log.info("topic hints backfill 완료: updated={}/{}", updatedCount, articles.size)
    }
}
