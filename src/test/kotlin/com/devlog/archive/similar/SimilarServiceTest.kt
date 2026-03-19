package com.devlog.archive.similar

import com.devlog.archive.article.ArticleSimilarityRepository
import com.devlog.archive.article.SimilarArticleRow
import com.devlog.archive.blog.BlogCacheService
import com.devlog.archive.blog.BlogEntity
import com.devlog.archive.embedding.EmbeddingClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDateTime

class SimilarServiceTest {

    private val articleSimilarityRepository = mock(ArticleSimilarityRepository::class.java)
    private val blogCacheService = mock(BlogCacheService::class.java)
    private val embeddingClient = mock(EmbeddingClient::class.java)
    private val similarService = SimilarService(
        articleSimilarityRepository,
        blogCacheService,
        embeddingClient,
    )

    @Test
    fun `returns empty items when embedding request fails`() {
        val request = SimilarRequest(
            title = "Spring",
            content = "Boot",
            topK = 3,
        )
        val exception = HttpClientErrorException.create(
            HttpStatus.UNAUTHORIZED,
            "Unauthorized",
            HttpHeaders.EMPTY,
            ByteArray(0),
            null,
        )

        `when`(embeddingClient.embed("Spring Spring Boot")).thenThrow(exception)

        val result = similarService.findSimilar(request)

        assertThat(result.items).isEmpty()
        verifyNoInteractions(articleSimilarityRepository)
    }

    @Test
    fun `reranks candidates with stronger title and keyword overlap`() {
        val request = SimilarRequest(
            title = "Redis Cache",
            content = "Spring Boot cache stampede lock strategy",
            topK = 3,
        )

        `when`(embeddingClient.embed("Redis Cache Redis Cache Spring Boot cache stampede lock strategy"))
            .thenReturn(listOf(0.1, 0.2))
        `when`(articleSimilarityRepository.findSimilar("[0.1,0.2]", 30))
            .thenReturn(
                listOf(
                    row(
                        id = 1L,
                        blogId = 1L,
                        title = "JPA Entity Design",
                        summary = "hibernate mapping tips",
                        similarity = 0.78,
                    ),
                    row(
                        id = 2L,
                        blogId = 2L,
                        title = "Redis Cache Stampede with Spring",
                        summary = "redis lock cache miss strategy",
                        similarity = 0.70,
                    ),
                    row(
                        id = 3L,
                        blogId = 3L,
                        title = "Kafka Consumer Retry",
                        summary = "dead letter queue",
                        similarity = 0.58,
                    ),
                )
            )
        `when`(blogCacheService.findAll()).thenReturn(
            listOf(
                blog(id = 1L, company = "Company A"),
                blog(id = 2L, company = "Company B"),
                blog(id = 3L, company = "Company C"),
            )
        )

        val result = similarService.findSimilar(request)

        assertThat(result.items).hasSize(2)
        assertThat(result.items.map { it.articleId }).containsExactly(2L, 1L)
        assertThat(result.items.first().company).isEqualTo("Company B")
        assertThat(result.items.first().similarity).isGreaterThan(result.items.last().similarity)
    }

    @Test
    fun `filters out weak vector matches without keyword signal`() {
        val request = SimilarRequest(
            title = "PostgreSQL Partitioning",
            content = "range partition index pruning strategy",
            topK = 2,
        )

        `when`(embeddingClient.embed("PostgreSQL Partitioning PostgreSQL Partitioning range partition index pruning strategy"))
            .thenReturn(listOf(0.5, 0.6))
        `when`(articleSimilarityRepository.findSimilar("[0.5,0.6]", 20))
            .thenReturn(
                listOf(
                    row(
                        id = 11L,
                        blogId = 1L,
                        title = "CSS Animation Timing",
                        summary = "animation easing transitions",
                        similarity = 0.49,
                    ),
                    row(
                        id = 12L,
                        blogId = 1L,
                        title = "Kubernetes Deployment Strategy",
                        summary = "rolling update probes",
                        similarity = 0.47,
                    ),
                )
            )
        `when`(blogCacheService.findAll()).thenReturn(listOf(blog(id = 1L, company = "Company A")))

        val result = similarService.findSimilar(request)

        assertThat(result.items).isEmpty()
    }

    @Test
    fun `returns fallback vector matches when strict rerank is empty`() {
        val request = SimilarRequest(
            title = "Terraform AWS Migration",
            content = "console infrastructure migration and operations",
            topK = 2,
        )

        `when`(embeddingClient.embed("Terraform AWS Migration Terraform AWS Migration console infrastructure migration and operations"))
            .thenReturn(listOf(0.7, 0.8))
        `when`(articleSimilarityRepository.findSimilar("[0.7,0.8]", 20))
            .thenReturn(
                listOf(
                    row(
                        id = 21L,
                        blogId = 1L,
                        title = "Large Scale Cloud Platform Story",
                        summary = "infrastructure migration operations reliability",
                        similarity = 0.63,
                    ),
                    row(
                        id = 22L,
                        blogId = 1L,
                        title = "Developer Productivity Metrics",
                        summary = "engineering process dashboard",
                        similarity = 0.51,
                    ),
                )
            )
        `when`(blogCacheService.findAll()).thenReturn(listOf(blog(id = 1L, company = "Company A")))

        val result = similarService.findSimilar(request)

        assertThat(result.items).hasSize(2)
        assertThat(result.items.map { it.articleId }).containsExactly(21L, 22L)
        assertThat(result.items.first().similarity).isGreaterThan(result.items.last().similarity)
    }

    private fun row(
        id: Long,
        blogId: Long,
        title: String,
        summary: String?,
        similarity: Double,
    ): SimilarArticleRow = TestSimilarArticleRow(
        id = id,
        blogId = blogId,
        title = title,
        url = "https://example.com/$id",
        summary = summary,
        publishedAt = LocalDateTime.of(2026, 3, 18, 10, 0),
        similarity = similarity,
    )

    private fun blog(id: Long, company: String) = BlogEntity(
        id = id,
        name = company,
        company = company,
        rssUrl = "https://example.com/rss/$id",
        homeUrl = "https://example.com/$id",
    )
}

private data class TestSimilarArticleRow(
    override val id: Long,
    override val title: String,
    override val url: String,
    override val summary: String?,
    override val publishedAt: LocalDateTime?,
    override val similarity: Double,
    override val blogId: Long,
) : SimilarArticleRow
