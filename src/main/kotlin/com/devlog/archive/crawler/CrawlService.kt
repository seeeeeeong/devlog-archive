package com.devlog.archive.crawler

import com.devlog.archive.core.EmbeddingClient
import com.devlog.archive.storage.*
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

@Service
class CrawlService(
    private val blogRepository: BlogRepository,
    private val articleRepository: ArticleRepository,
    private val crawlLogRepository: CrawlLogRepository,
    private val rssFeedParser: RssFeedParser,
    private val embeddingClient: EmbeddingClient,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val crawling = AtomicBoolean(false)

    fun crawlAll() {
        if (!crawling.compareAndSet(false, true)) {
            log.warn("크롤링이 이미 실행 중입니다. 건너뜀.")
            return
        }
        try {
            val blogs = blogRepository.findAllByActiveTrue()
            log.info("크롤링 시작: 대상 블로그 {}개", blogs.size)
            blogs.forEach { blog ->
                try {
                    crawlBlog(blog)
                } catch (e: Exception) {
                    log.error("블로그 크롤링 실패: company={}, error={}", blog.company, e.message)
                }
            }
        } finally {
            crawling.set(false)
        }
    }

    @Transactional
    fun crawlBlog(blog: BlogEntity) {
        val startedAt = LocalDateTime.now()
        val log = CrawlLogEntity(blog = blog, startedAt = startedAt, status = "IN_PROGRESS")
            .let { crawlLogRepository.save(it) }

        var newCount = 0
        var status = "SUCCESS"
        var message: String? = null

        try {
            val parsedArticles = rssFeedParser.parse(blog.rssUrl)
            this.log.info("RSS 파싱 완료: company={}, count={}", blog.company, parsedArticles.size)

            parsedArticles.forEach { parsed ->
                val urlHash = sha256(parsed.url)
                if (articleRepository.existsByUrlHash(urlHash)) return@forEach

                val article = articleRepository.save(
                    ArticleEntity(
                        blog = blog,
                        title = parsed.title,
                        url = parsed.url,
                        urlHash = urlHash,
                        summary = parsed.summary,
                        publishedAt = parsed.publishedAt,
                    )
                )

                saveEmbedding(article, parsed)
                newCount++
            }
        } catch (e: Exception) {
            status = "FAIL"
            message = e.message
            this.log.error("크롤링 실패: company={}, error={}", blog.company, e.message)
        } finally {
            log.finishedAt = LocalDateTime.now()
            log.newCount = newCount
            log.status = status
            log.message = message
            crawlLogRepository.save(log)
        }

        this.log.info("크롤링 완료: company={}, status={}, newCount={}", blog.company, status, newCount)
    }

    private fun saveEmbedding(article: ArticleEntity, parsed: ParsedArticle) {
        val text = "${article.title} ${parsed.summary ?: ""}".trim()
        val vector = try {
            embeddingClient.embed(text)
        } catch (e: Exception) {
            log.warn("임베딩 생성 실패: articleId={}, error={}", article.id, e.message)
            return
        }

        val vectorLiteral = vector.joinToString(",", "[", "]")
        jdbcTemplate.update(
            "INSERT INTO article_embeddings (article_id, embedding) VALUES (?, ?::vector)",
            article.id,
            vectorLiteral,
        )
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
