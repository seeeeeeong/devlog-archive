package com.devlog.archive.admin

import com.devlog.archive.article.ArticleEmbeddingRepository
import com.devlog.archive.article.ArticleRepository
import com.devlog.archive.article.ArticleTopicHintExtractor
import com.devlog.archive.blog.BlogRepository
import com.devlog.archive.config.AdminProperties
import com.devlog.archive.crawl.CrawlService
import com.devlog.archive.embedding.EmbeddingClient
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class AdminController(
    private val crawlService: CrawlService,
    private val blogRepository: BlogRepository,
    private val articleRepository: ArticleRepository,
    private val articleEmbeddingRepository: ArticleEmbeddingRepository,
    private val embeddingClient: EmbeddingClient,
    private val admin: AdminProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    @PostMapping("/admin/crawl")
    fun triggerCrawl(
        @RequestHeader("X-Admin-Key", required = false) key: String?,
    ): ResponseEntity<Map<String, String>> {
        if (key != admin.apiKey) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        crawlService.crawlAllAsync()
        return ResponseEntity.ok(mapOf("message" to "크롤링 시작됨"))
    }

    @GetMapping("/blogs")
    fun listBlogs(
        @RequestHeader("X-Admin-Key", required = false) key: String?,
    ): ResponseEntity<Any> {
        if (key != admin.apiKey) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val blogs = blogRepository.findAllByActiveTrue()
        return ResponseEntity.ok(blogs.map {
            mapOf("id" to it.id, "company" to it.company, "name" to it.name)
        })
    }

    @GetMapping("/articles")
    fun listArticles(
        @RequestHeader("X-Admin-Key", required = false) key: String?,
    ): ResponseEntity<Any> {
        if (key != admin.apiKey) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val articles = articleRepository.findAll(PageRequest.of(0, 20))
        return ResponseEntity.ok(mapOf(
            "total" to articles.totalElements,
            "items" to articles.content.map {
                mapOf(
                    "id" to it.id,
                    "title" to it.title,
                    "company" to it.blog.company,
                    "publishedAt" to it.publishedAt,
                )
            }
        ))
    }

    @PostMapping("/admin/backfill-embeddings")
    fun backfillEmbeddings(
        @RequestHeader("X-Admin-Key", required = false) key: String?,
    ): ResponseEntity<Map<String, Any>> {
        if (key != admin.apiKey) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

        val articles = articleRepository.findAll()
        var success = 0
        var failed = 0

        articles.forEach { article ->
            try {
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
                success++
            } catch (e: Exception) {
                failed++
                log.warn("backfill 실패: articleId={}, error={}", article.id, e.message)
            }
        }

        log.info("backfill 완료: success={}, failed={}", success, failed)
        return ResponseEntity.ok(mapOf("success" to success, "failed" to failed, "total" to articles.size))
    }
}
