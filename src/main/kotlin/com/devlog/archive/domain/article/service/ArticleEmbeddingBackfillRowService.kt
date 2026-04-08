package com.devlog.archive.domain.article.service

import com.devlog.archive.domain.article.entity.ArticleEntity
import com.devlog.archive.domain.article.repository.ArticleEmbeddingRepository
import com.devlog.archive.domain.article.repository.ArticleRepository
import com.devlog.archive.domain.embedding.client.EmbeddingClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticleEmbeddingBackfillRowService(
    private val articleRepository: ArticleRepository,
    private val articleEmbeddingRepository: ArticleEmbeddingRepository,
    private val embeddingClient: EmbeddingClient,
) {
    @Transactional
    fun process(article: ArticleEntity) {
        val topicHints = ArticleTopicHintExtractor.extract(article.title, article.summary)
        val topicHintsStorage = ArticleTopicHintExtractor.toStorageValue(topicHints)
        articleRepository.updateTopicHints(article.id, topicHintsStorage)

        val text = ArticleTopicHintExtractor.buildEmbeddingText(
            title = article.title,
            summary = article.summary,
            topicHints = topicHints,
        )
        val vector = embeddingClient.embed(text)
        articleEmbeddingRepository.upsert(article.id, vector)
    }
}
