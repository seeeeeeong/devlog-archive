package com.devlog.archive.embedding

import com.devlog.archive.config.OpenAiProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient

@Component
class EmbeddingClient(private val props: OpenAiProperties) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient = RestClient.builder()
        .baseUrl(props.baseUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()

    @Retryable(
        retryFor = [HttpServerErrorException::class, ResourceAccessException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0),
    )
    fun embed(text: String): List<Double> {
        val truncated = text.take(8000)
        log.debug("임베딩 생성 요청: textLength={}", truncated.length)

        val response = restClient.post()
            .uri("/v1/embeddings")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${props.apiKey}")
            .body(EmbeddingRequest(model = props.embeddingModel, input = truncated))
            .retrieve()
            .body(EmbeddingResponse::class.java)
            ?: throw IllegalStateException("OpenAI 응답이 비어있습니다")

        return response.data.first().embedding
    }
}

data class EmbeddingRequest(val model: String, val input: String)
data class EmbeddingResponse(val `data`: List<EmbeddingData>)
data class EmbeddingData(val embedding: List<Double>)
