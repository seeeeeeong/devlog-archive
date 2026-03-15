package com.devlog.archive.core

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class EmbeddingClient(
    @Value("\${openai.api-key}") private val apiKey: String,
    @Value("\${openai.embedding-model}") private val model: String,
    @Value("\${openai.base-url}") private val baseUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()

    fun embed(text: String): List<Double> {
        val truncated = text.take(8000)  // OpenAI 토큰 한도 여유
        log.debug("임베딩 생성 요청: textLength={}", truncated.length)

        val response = restClient.post()
            .uri("/v1/embeddings")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .body(EmbeddingRequest(model = model, input = truncated))
            .retrieve()
            .body(EmbeddingResponse::class.java)
            ?: throw IllegalStateException("OpenAI 응답이 비어있습니다")

        return response.data.first().embedding
    }
}

data class EmbeddingRequest(
    val model: String,
    val input: String,
)

data class EmbeddingResponse(
    val `data`: List<EmbeddingData>,
)

data class EmbeddingData(
    val embedding: List<Double>,
)
