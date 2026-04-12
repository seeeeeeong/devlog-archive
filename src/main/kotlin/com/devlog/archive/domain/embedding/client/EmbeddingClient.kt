package com.devlog.archive.domain.embedding.client

import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Component

@Component
class EmbeddingClient(private val embeddingModel: EmbeddingModel) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun embed(text: String): List<Double> {
        val truncated = text.take(8000)
        log.debug("임베딩 생성 요청: textLength={}", truncated.length)

        val response = embeddingModel.embed(truncated)
        return response.map { it.toDouble() }
    }
}
