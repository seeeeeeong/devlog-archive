package com.devlog.archive.similar

import com.devlog.archive.article.ArticleSimilarityRepository
import com.devlog.archive.article.ArticleTopicHintExtractor
import com.devlog.archive.article.LexicalArticleRow
import com.devlog.archive.article.SimilarArticleRow
import com.devlog.archive.blog.BlogCacheService
import com.devlog.archive.blog.BlogEntity
import com.devlog.archive.config.SimilarProperties
import com.devlog.archive.embedding.EmbeddingClient
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
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
    private val meterRegistry = SimpleMeterRegistry()
    private val props = SimilarProperties()
    private val similarService = SimilarService(
        articleSimilarityRepository,
        blogCacheService,
        embeddingClient,
        meterRegistry,
        props,
    )

    init {
        `when`(articleSimilarityRepository.findLexicalCandidates(anyInt())).thenReturn(emptyList())
        `when`(articleSimilarityRepository.findByFullTextSearch(anyString(), anyInt())).thenReturn(emptyList())
    }

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
                        similarity = 0.29,
                    ),
                    row(
                        id = 12L,
                        blogId = 1L,
                        title = "Kubernetes Deployment Strategy",
                        summary = "rolling update probes",
                        similarity = 0.27,
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

        assertThat(result.items).hasSize(1)
        assertThat(result.items.single().articleId).isEqualTo(21L)
        assertThat(result.items.single().similarity).isGreaterThan(0.49)
    }

    @Test
    fun `returns top vector match as last resort when fallback is still empty`() {
        val request = SimilarRequest(
            title = "Kafka Consumer Idempotency",
            content = "consumer deduplication and final confirmation",
            topK = 3,
        )

        `when`(embeddingClient.embed("Kafka Consumer Idempotency Kafka Consumer Idempotency consumer deduplication and final confirmation"))
            .thenReturn(listOf(0.9, 1.0))
        `when`(articleSimilarityRepository.findSimilar("[0.9,1.0]", 30))
            .thenReturn(
                listOf(
                    row(
                        id = 31L,
                        blogId = 1L,
                        title = "Messaging Platform Overview",
                        summary = "distributed stream processing architecture",
                        similarity = 0.39,
                    ),
                    row(
                        id = 32L,
                        blogId = 1L,
                        title = "Frontend Bundle Optimization",
                        summary = "code split asset pipeline",
                        similarity = 0.34,
                    ),
                )
            )
        `when`(blogCacheService.findAll()).thenReturn(listOf(blog(id = 1L, company = "Company A")))

        val result = similarService.findSimilar(request)

        assertThat(result.items).hasSize(1)
        assertThat(result.items.single().articleId).isEqualTo(31L)
        assertThat(result.items.single().similarity).isEqualTo(0.39)
    }

    @Test
    fun `returns lexical only candidate when topic hints strongly match`() {
        val request = SimilarRequest(
            title = "Outbox Pattern Kafka",
            content = "reliable event delivery for coupon system",
            topicHints = listOf("Outbox", "Kafka"),
            topK = 2,
        )

        `when`(embeddingClient.embed("Outbox Pattern Kafka Outbox Pattern Kafka Outbox Kafka reliable event delivery for coupon system"))
            .thenReturn(listOf(0.3, 0.4))
        `when`(articleSimilarityRepository.findSimilar("[0.3,0.4]", 20))
            .thenReturn(emptyList())
        `when`(articleSimilarityRepository.findByFullTextSearch(anyString(), anyInt()))
            .thenReturn(
                listOf(
                    lexicalRow(
                        id = 41L,
                        blogId = 1L,
                        title = "Outbox Kafka Messaging at Scale",
                        summary = "reliable delivery with outbox and kafka",
                    )
                )
            )
        `when`(blogCacheService.findAll()).thenReturn(listOf(blog(id = 1L, company = "Company A")))

        val result = similarService.findSimilar(request)

        assertThat(result.items).hasSize(1)
        assertThat(result.items.single().articleId).isEqualTo(41L)
        assertThat(result.items.single().similarity).isGreaterThan(0.5)
    }

    @Test
    fun `uses stored candidate topic hints during reranking`() {
        val request = SimilarRequest(
            title = "Redis 장애를 사용자 장애로 만들지 않는 법",
            content = "장애 격리와 fallback 전략",
            topicHints = listOf("Redis", "Failure Handling"),
            topK = 2,
        )

        `when`(embeddingClient.embed("Redis 장애를 사용자 장애로 만들지 않는 법 Redis 장애를 사용자 장애로 만들지 않는 법 Redis Failure Handling 장애 격리와 fallback 전략"))
            .thenReturn(listOf(0.2, 0.3))
        `when`(articleSimilarityRepository.findSimilar("[0.2,0.3]", 20))
            .thenReturn(emptyList())
        `when`(articleSimilarityRepository.findByFullTextSearch(anyString(), anyInt()))
            .thenReturn(
                listOf(
                    lexicalRow(
                        id = 51L,
                        blogId = 1L,
                        title = "서비스 장애 복구 패턴",
                        summary = "트래픽 급증 상황에서 fallback과 격리를 적용한 운영 경험",
                        topicHints = listOf("Redis", "Failure Handling"),
                    )
                )
            )
        `when`(blogCacheService.findAll()).thenReturn(listOf(blog(id = 1L, company = "Company A")))

        val result = similarService.findSimilar(request)

        assertThat(result.items).hasSize(1)
        assertThat(result.items.single().articleId).isEqualTo(51L)
        assertThat(result.items.single().similarity).isGreaterThan(0.45)
    }

    @Test
    fun `diversity filter limits results per blog`() {
        val request = SimilarRequest(
            title = "Redis Performance",
            content = "redis latency optimization",
            topK = 5,
        )

        `when`(embeddingClient.embed("Redis Performance Redis Performance redis latency optimization"))
            .thenReturn(listOf(0.1, 0.2))
        `when`(articleSimilarityRepository.findSimilar("[0.1,0.2]", 50))
            .thenReturn(
                listOf(
                    row(id = 1L, blogId = 1L, title = "Redis Cluster Performance", summary = "redis latency tuning", similarity = 0.80),
                    row(id = 2L, blogId = 1L, title = "Redis Memory Optimization", summary = "redis memory performance", similarity = 0.78),
                    row(id = 3L, blogId = 1L, title = "Redis Sentinel Setup", summary = "redis high availability performance", similarity = 0.76),
                    row(id = 4L, blogId = 2L, title = "Redis Cache Layer Design", summary = "redis performance caching", similarity = 0.74),
                )
            )
        `when`(blogCacheService.findAll()).thenReturn(
            listOf(
                blog(id = 1L, company = "Company A"),
                blog(id = 2L, company = "Company B"),
            )
        )

        val result = similarService.findSimilar(request)

        val companyACounts = result.items.count { it.company == "Company A" }
        assertThat(companyACounts).isLessThanOrEqualTo(props.maxPerBlog)
        assertThat(result.items.any { it.company == "Company B" }).isTrue()
    }

    private fun row(
        id: Long,
        blogId: Long,
        title: String,
        summary: String?,
        similarity: Double,
        topicHints: List<String> = emptyList(),
    ): SimilarArticleRow = TestSimilarArticleRow(
        id = id,
        blogId = blogId,
        title = title,
        url = "https://example.com/$id",
        summary = summary,
        topicHints = ArticleTopicHintExtractor.toStorageValue(topicHints),
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

    private fun lexicalRow(
        id: Long,
        blogId: Long,
        title: String,
        summary: String?,
        topicHints: List<String> = emptyList(),
    ): LexicalArticleRow = TestLexicalArticleRow(
        id = id,
        blogId = blogId,
        title = title,
        url = "https://example.com/$id",
        summary = summary,
        topicHints = ArticleTopicHintExtractor.toStorageValue(topicHints),
        publishedAt = LocalDateTime.of(2026, 3, 18, 10, 0),
    )
}

private data class TestSimilarArticleRow(
    override val id: Long,
    override val title: String,
    override val url: String,
    override val summary: String?,
    override val topicHints: String?,
    override val publishedAt: LocalDateTime?,
    override val similarity: Double,
    override val blogId: Long,
) : SimilarArticleRow

private data class TestLexicalArticleRow(
    override val id: Long,
    override val title: String,
    override val url: String,
    override val summary: String?,
    override val topicHints: String?,
    override val publishedAt: LocalDateTime?,
    override val blogId: Long,
) : LexicalArticleRow
