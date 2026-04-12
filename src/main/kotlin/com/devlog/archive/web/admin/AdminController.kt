package com.devlog.archive.web.admin

import com.devlog.archive.config.AdminProperties
import com.devlog.archive.domain.article.repository.ArticleRepository
import com.devlog.archive.domain.article.service.ArticleEmbeddingBackfillService
import com.devlog.archive.domain.blog.repository.BlogRepository
import com.devlog.archive.domain.crawl.service.CrawlService
import com.devlog.archive.domain.similar.service.SimilarAnalyticsService
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class AdminController(
    private val crawlService: CrawlService,
    private val blogRepository: BlogRepository,
    private val articleRepository: ArticleRepository,
    private val articleEmbeddingBackfillService: ArticleEmbeddingBackfillService,
    private val similarAnalyticsService: SimilarAnalyticsService,
    private val admin: AdminProperties,
) {
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
        val articles = articleRepository.findAllWithBlog(PageRequest.of(0, 20))
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
        val result = articleEmbeddingBackfillService.backfillAll()
        return ResponseEntity.ok(mapOf("success" to result.success, "failed" to result.failed, "total" to result.total))
    }

    @GetMapping("/admin/analytics/clicks")
    fun clickAnalytics(
        @RequestHeader("X-Admin-Key", required = false) key: String?,
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<Map<String, Any>> {
        if (key != admin.apiKey) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        return ResponseEntity.ok(similarAnalyticsService.getClickSummary(days))
    }
}
