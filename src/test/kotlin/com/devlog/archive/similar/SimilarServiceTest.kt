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
        `when`(articleSimilarityRepository.findByFullTextSearch(anyString(), anyInt())).thenReturn(emptyList())
    }

    @Test
    fun `falls back to lexical results when embedding request fails`() {
        val request = SimilarRequest(
            title = "Spring Boot Cache",
            content = "cache stampede lock strategy",
            topK = 3,
        )
        val exception = HttpClientErrorException.create(
            HttpStatus.UNAUTHORIZED,
            "Unauthorized",
            HttpHeaders.EMPTY,
            ByteArray(0),
            null,
        )

        `when`(embeddingClient.embed(anyString())).thenThrow(exception)
        `when`(articleSimilarityRepository.findByFullTextSearch(anyString(), anyInt()))
            .thenReturn(
                listOf(
                    lexicalRow(
                        id = 101L,
                        blogId = 1L,
                        title = "Spring Boot Cache Stampede Prevention",
                        summary = "cache stampede lock strategy spring boot",
                    ),
                )
            )
        `when`(blogCacheService.findAll()).thenReturn(listOf(blog(id = 1L, company = "Company A")))

        val result = similarService.findSimilar(request)

        assertThat(result.items).isNotEmpty
        assertThat(result.items.first().articleId).isEqualTo(101L)
    }

    @Test
    fun `returns empty when embedding fails and no lexical candidates exist`() {
        val request = SimilarRequest(
            title = "xyzzy",
            content = "qwerty",
            topK = 3,
        )
        val exception = HttpClientErrorException.create(
            HttpStatus.UNAUTHORIZED,
            "Unauthorized",
            HttpHeaders.EMPTY,
            ByteArray(0),
            null,
        )

        `when`(embeddingClient.embed(anyString())).thenThrow(exception)

        val result = similarService.findSimilar(request)

        assertThat(result.items).isEmpty()
    }

    @Test
    fun `RRF boosts candidates appearing in both vector and FTS results`() {
        val request = SimilarRequest(
            title = "Redis Cache",
            content = "Spring Boot cache stampede lock strategy",
            topK = 3,
        )

        `when`(embeddingClient.embed(anyString()))
            .thenReturn(listOf(0.1, 0.2))
        `when`(articleSimilarityRepository.findSimilar("[0.1,0.2]", props.vectorCandidateLimit))
            .thenReturn(
                listOf(
                    row(id = 1L, blogId = 1L, title = "Spring Boot Cache Configuration", summary = "cache strategy", similarity = 0.66),
                    row(id = 2L, blogId = 2L, title = "Redis Cache Stampede with Spring", summary = "redis lock cache", similarity = 0.65),
                    row(id = 3L, blogId = 3L, title = "Kafka Consumer Retry", summary = "dead letter queue", similarity = 0.58),
                )
            )
        // Only article 2 appears in FTS — it should get boosted above article 1 (vector-only)
        `when`(articleSimilarityRepository.findByFullTextSearch(anyString(), anyInt()))
            .thenReturn(
                listOf(
                    lexicalRow(id = 2L, blogId = 2L, title = "Redis Cache Stampede with Spring", summary = "redis lock cache"),
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

        assertThat(result.items).isNotEmpty
        // Article 2 appears in both lists: vector rank 2 + FTS rank 1 → highest combined RRF
        assertThat(result.items.first().articleId).isEqualTo(2L)
        // Article 3 only in vector at rank 3, should have lowest RRF score
        val article3 = result.items.find { it.articleId == 3L }
        if (article3 != null) {
            assertThat(article3.similarity).isLessThan(result.items.first().similarity)
        }
    }

    @Test
    fun `filters candidates below minimum RRF score`() {
        val request = SimilarRequest(
            title = "PostgreSQL Partitioning",
            content = "range partition index pruning strategy",
            topK = 2,
        )

        // Use very high rrfK so scores are very low
        val strictProps = SimilarProperties(minimumRrfScore = 0.05)
        val strictService = SimilarService(
            articleSimilarityRepository,
            blogCacheService,
            embeddingClient,
            meterRegistry,
            strictProps,
        )

        `when`(embeddingClient.embed(anyString()))
            .thenReturn(listOf(0.5, 0.6))
        // Only 2 candidates at high ranks → low RRF scores
        `when`(articleSimilarityRepository.findSimilar("[0.5,0.6]", strictProps.vectorCandidateLimit))
            .thenReturn(
                listOf(
                    row(id = 11L, blogId = 1L, title = "CSS Animation", summary = "animation", similarity = 0.29),
                )
            )
        `when`(blogCacheService.findAll()).thenReturn(listOf(blog(id = 1L, company = "Company A")))

        val result = strictService.findSimilar(request)

        // 1/(60+1) = 0.0164 which is below 0.05 threshold
        assertThat(result.items).isEmpty()
    }

    @Test
    fun `diversity filter limits results per company`() {
        val request = SimilarRequest(
            title = "Redis Performance",
            content = "redis latency optimization",
            topK = 5,
        )

        `when`(embeddingClient.embed(anyString()))
            .thenReturn(listOf(0.1, 0.2))
        `when`(articleSimilarityRepository.findSimilar("[0.1,0.2]", props.vectorCandidateLimit))
            .thenReturn(
                listOf(
                    row(id = 1L, blogId = 1L, title = "Redis Cluster Performance", summary = "redis latency tuning", similarity = 0.80),
                    row(id = 2L, blogId = 1L, title = "Redis Memory Optimization", summary = "redis memory performance", similarity = 0.78),
                    row(id = 3L, blogId = 1L, title = "Redis Sentinel Setup", summary = "redis high availability", similarity = 0.76),
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

    @Test
    fun `diversity filter limits by company not blog id`() {
        val request = SimilarRequest(
            title = "Redis Performance",
            content = "redis latency optimization",
            topK = 5,
        )

        `when`(embeddingClient.embed(anyString()))
            .thenReturn(listOf(0.1, 0.2))
        `when`(articleSimilarityRepository.findSimilar("[0.1,0.2]", props.vectorCandidateLimit))
            .thenReturn(
                listOf(
                    row(id = 1L, blogId = 1L, title = "Redis Cluster Performance", summary = "redis latency tuning", similarity = 0.80),
                    row(id = 2L, blogId = 2L, title = "Redis Memory Optimization", summary = "redis memory performance", similarity = 0.78),
                    row(id = 3L, blogId = 3L, title = "Redis Sentinel Setup", summary = "redis high availability", similarity = 0.76),
                    row(id = 4L, blogId = 4L, title = "Redis Cache Layer Design", summary = "redis performance caching", similarity = 0.74),
                )
            )
        `when`(blogCacheService.findAll()).thenReturn(
            listOf(
                blog(id = 1L, company = "카카오"),
                blog(id = 2L, company = "카카오"),
                blog(id = 3L, company = "카카오"),
                blog(id = 4L, company = "토스"),
            )
        )

        val result = similarService.findSimilar(request)

        val kakaoCount = result.items.count { it.company == "카카오" }
        assertThat(kakaoCount).isLessThanOrEqualTo(props.maxPerBlog)
        assertThat(result.items.any { it.company == "토스" }).isTrue()
    }

    @Test
    fun `vector-only candidate gets single RRF term`() {
        val request = SimilarRequest(
            title = "Kafka Streams",
            content = "stream processing pipeline",
            topK = 3,
        )

        `when`(embeddingClient.embed(anyString()))
            .thenReturn(listOf(0.3, 0.4))
        `when`(articleSimilarityRepository.findSimilar("[0.3,0.4]", props.vectorCandidateLimit))
            .thenReturn(
                listOf(
                    row(id = 1L, blogId = 1L, title = "Kafka Streams Processing", summary = "stream pipeline", similarity = 0.75),
                )
            )
        `when`(blogCacheService.findAll()).thenReturn(listOf(blog(id = 1L, company = "Company A")))

        val result = similarService.findSimilar(request)

        assertThat(result.items).hasSize(1)
        // score = 1/(60+1) ≈ 0.0164, above default minimum 0.015
        assertThat(result.items.first().similarity).isGreaterThan(0.015)
        assertThat(result.items.first().similarity).isLessThan(0.02)
    }

    @Test
    fun `FTS-only candidate gets single RRF term`() {
        val request = SimilarRequest(
            title = "Outbox Pattern Kafka",
            content = "reliable event delivery",
            topicHints = listOf("Outbox", "Kafka"),
            topK = 2,
        )

        `when`(embeddingClient.embed(anyString()))
            .thenReturn(listOf(0.3, 0.4))
        `when`(articleSimilarityRepository.findSimilar("[0.3,0.4]", props.vectorCandidateLimit))
            .thenReturn(emptyList())
        `when`(articleSimilarityRepository.findByFullTextSearch(anyString(), anyInt()))
            .thenReturn(
                listOf(
                    lexicalRow(id = 41L, blogId = 1L, title = "Outbox Kafka Messaging at Scale", summary = "reliable delivery with outbox"),
                )
            )
        `when`(blogCacheService.findAll()).thenReturn(listOf(blog(id = 1L, company = "Company A")))

        val result = similarService.findSimilar(request)

        assertThat(result.items).hasSize(1)
        assertThat(result.items.single().articleId).isEqualTo(41L)
        assertThat(result.items.single().similarity).isGreaterThan(0.015)
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
