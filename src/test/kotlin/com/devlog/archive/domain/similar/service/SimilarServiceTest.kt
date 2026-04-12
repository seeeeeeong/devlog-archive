package com.devlog.archive.domain.similar.service

import com.devlog.archive.domain.article.repository.ArticleSimilarityRepository
import com.devlog.archive.domain.article.repository.LexicalArticleRow
import com.devlog.archive.domain.article.repository.SimilarArticleRow
import com.devlog.archive.domain.blog.service.BlogCacheService
import com.devlog.archive.domain.blog.entity.BlogEntity
import com.devlog.archive.config.SimilarProperties
import com.devlog.archive.domain.embedding.client.EmbeddingClient
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
import com.devlog.archive.domain.similar.service.dto.SimilarQuery

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
        `when`(articleSimilarityRepository.findLexicalCandidates(anyInt())).thenReturn(emptyList())
    }

    @Test
    fun `falls back to lexical results when embedding request fails`() {
        val request = SimilarQuery(
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

        assertThat(result).isNotEmpty
        assertThat(result.first().articleId).isEqualTo(101L)
    }

    @Test
    fun `returns empty when embedding fails and no lexical candidates exist`() {
        val request = SimilarQuery(
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

        assertThat(result).isEmpty()
    }

    @Test
    fun `RRF boosts candidates appearing in both vector and FTS results`() {
        val request = SimilarQuery(
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

        assertThat(result).isNotEmpty
        assertThat(result.first().articleId).isEqualTo(2L)
        val article3 = result.find { it.articleId == 3L }
        if (article3 != null) {
            assertThat(article3.similarity).isLessThan(result.first().similarity)
        }
    }

    @Test
    fun `filters candidates below minimum RRF score`() {
        val request = SimilarQuery(
            title = "PostgreSQL Partitioning",
            content = "range partition index pruning strategy",
            topK = 2,
        )

        val strictProps = SimilarProperties(minimumRrfScore = 0.02)
        val strictService = SimilarService(
            articleSimilarityRepository,
            blogCacheService,
            embeddingClient,
            meterRegistry,
            strictProps,
        )

        `when`(embeddingClient.embed(anyString()))
            .thenReturn(listOf(0.5, 0.6))
        `when`(articleSimilarityRepository.findSimilar("[0.5,0.6]", strictProps.vectorCandidateLimit))
            .thenReturn(
                listOf(
                    row(id = 11L, blogId = 1L, title = "CSS Animation", summary = "animation", similarity = 0.29),
                )
            )
        `when`(articleSimilarityRepository.findLexicalCandidates(anyInt())).thenReturn(emptyList())
        `when`(blogCacheService.findAll()).thenReturn(listOf(blog(id = 1L, company = "Company A")))

        val result = strictService.findSimilar(request)

        assertThat(result).isEmpty()
    }

    @Test
    fun `single-source candidates pass default threshold up to high ranks`() {
        val request = SimilarQuery(
            title = "Redis Performance",
            content = "redis latency optimization",
            topK = 10,
        )

        val candidates = (1L..10L).map { i ->
            row(id = i, blogId = i, title = "Redis Article $i", summary = "redis content $i", similarity = 0.80 - i * 0.02)
        }
        val blogs = (1L..10L).map { blog(id = it, company = "Company $it") }

        `when`(embeddingClient.embed(anyString()))
            .thenReturn(listOf(0.1, 0.2))
        `when`(articleSimilarityRepository.findSimilar("[0.1,0.2]", props.vectorCandidateLimit))
            .thenReturn(candidates)
        `when`(articleSimilarityRepository.findLexicalCandidates(anyInt())).thenReturn(emptyList())
        `when`(blogCacheService.findAll()).thenReturn(blogs)

        val result = similarService.findSimilar(request)

        assertThat(result).hasSize(10)
    }

    @Test
    fun `diversity filter limits results per company`() {
        val request = SimilarQuery(
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

        val companyACounts = result.count { it.company == "Company A" }
        assertThat(companyACounts).isLessThanOrEqualTo(props.maxPerBlog)
        assertThat(result.any { it.company == "Company B" }).isTrue()
    }

    @Test
    fun `diversity filter limits by company not blog id`() {
        val request = SimilarQuery(
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

        val kakaoCount = result.count { it.company == "카카오" }
        assertThat(kakaoCount).isLessThanOrEqualTo(props.maxPerBlog)
        assertThat(result.any { it.company == "토스" }).isTrue()
    }

    @Test
    fun `vector-only candidate gets single RRF term`() {
        val request = SimilarQuery(
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

        assertThat(result).hasSize(1)
        assertThat(result.first().similarity).isGreaterThan(0.008)
        assertThat(result.first().similarity).isLessThan(0.02)
    }

    @Test
    fun `FTS-only candidate gets single RRF term`() {
        val request = SimilarQuery(
            title = "Outbox Pattern Kafka",
            content = "reliable event delivery",
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

        assertThat(result).hasSize(1)
        assertThat(result.single().articleId).isEqualTo(41L)
        assertThat(result.single().similarity).isGreaterThan(0.008)
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

    private fun lexicalRow(
        id: Long,
        blogId: Long,
        title: String,
        summary: String?,
    ): LexicalArticleRow = TestLexicalArticleRow(
        id = id,
        blogId = blogId,
        title = title,
        url = "https://example.com/$id",
        summary = summary,
        publishedAt = LocalDateTime.of(2026, 3, 18, 10, 0),
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

private data class TestLexicalArticleRow(
    override val id: Long,
    override val title: String,
    override val url: String,
    override val summary: String?,
    override val publishedAt: LocalDateTime?,
    override val blogId: Long,
) : LexicalArticleRow
