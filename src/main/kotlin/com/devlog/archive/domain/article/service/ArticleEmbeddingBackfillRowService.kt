package com.devlog.archive.domain.article.service

import com.devlog.archive.domain.article.entity.ArticleEntity
import com.devlog.archive.domain.article.repository.ArticleEmbeddingRepository
import com.devlog.archive.domain.embedding.client.EmbeddingClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticleEmbeddingBackfillRowService(
    private val articleEmbeddingRepository: ArticleEmbeddingRepository,
    private val embeddingClient: EmbeddingClient,
) {
    @Transactional
    fun process(article: ArticleEntity) {
        val text = buildEmbeddingText(article.title, article.summary)
        val vector = embeddingClient.embed(text)
        articleEmbeddingRepository.upsert(article.id, vector)
    }
}

fun buildEmbeddingText(title: String, summary: String?): String {
    return buildList {
        add("Title: $title")
        summary?.takeIf { it.isNotBlank() }?.let { add("Summary: $it") }
    }.joinToString("\n").trim()
}
