package com.devlog.archive.api

import com.devlog.archive.config.AdminProperties
import com.devlog.archive.crawler.CrawlService
import com.devlog.archive.storage.ArticleRepository
import com.devlog.archive.storage.BlogRepository
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
    private val admin: AdminProperties,
) {
    @PostMapping("/admin/crawl")
    fun triggerCrawl(
        @RequestHeader("X-Admin-Key", required = false) key: String?,
    ): ResponseEntity<Map<String, String>> {
        if (key != admin.apiKey) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        Thread { crawlService.crawlAll() }.start()
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
}
