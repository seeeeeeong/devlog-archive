package com.devlog.archive.crawl

import com.devlog.archive.article.ArticleEmbeddingRepository
import com.devlog.archive.article.ArticleTopicHintExtractor
import com.devlog.archive.blog.BlogEntity
import com.devlog.archive.blog.BlogRepository
import com.devlog.archive.embedding.EmbeddingClient
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class CrawlService(
    private val blogRepository: BlogRepository,
    private val articleStoreService: ArticleStoreService,
    private val rssFeedParser: RssFeedParser,
    private val embeddingClient: EmbeddingClient,
    private val articleEmbeddingRepository: ArticleEmbeddingRepository,
    private val contentScraper: ContentScraper,
    private val cacheManager: CacheManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val crawling = AtomicBoolean(false)

    @Async
    fun crawlAllAsync() = crawlAll()

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
            cacheManager.getCache("similar")?.clear()
            log.info("크롤링 완료 후 similar 캐시 초기화")
        } finally {
            crawling.set(false)
        }
    }

    fun crawlBlog(blog: BlogEntity) {
        val crawlLog = articleStoreService.startLog(blog)
        var newCount = 0
        var status = "SUCCESS"
        var message: String? = null

        try {
            val parsedArticles = rssFeedParser.parse(blog.rssUrl)
            log.info("RSS 파싱 완료: company={}, count={}", blog.company, parsedArticles.size)

            parsedArticles.forEach { parsed ->
                val enrichedSummary = enrichSummary(parsed)
                val enrichedParsed = if (enrichedSummary != null) parsed.copy(summary = enrichedSummary) else parsed

                val article = articleStoreService.saveArticleIfNew(blog, enrichedParsed) ?: return@forEach
                newCount++

                try {
                    val text = ArticleTopicHintExtractor.buildEmbeddingText(
                        title = article.title,
                        summary = article.summary,
                        topicHints = ArticleTopicHintExtractor.fromStorageValue(article.topicHints),
                    )
                    val vector = embeddingClient.embed(text)
                    articleEmbeddingRepository.upsert(article.id, vector)
                } catch (e: Exception) {
                    log.warn("임베딩 저장 실패: articleId={}, error={}", article.id, e.message)
                }
            }
        } catch (e: Exception) {
            status = "FAIL"
            message = e.message
            log.error("크롤링 실패: company={}, error={}", blog.company, e.message)
        } finally {
            articleStoreService.finishLog(crawlLog, newCount, status, message)
        }

        log.info("크롤링 완료: company={}, status={}, newCount={}", blog.company, status, newCount)
    }

    private fun enrichSummary(parsed: ParsedArticle): String? {
        val rssSummary = parsed.summary.orEmpty()
        if (rssSummary.length >= 800) return null

        val scraped = contentScraper.scrape(parsed.url) ?: return null
        return if (scraped.length > rssSummary.length) scraped else null
    }
}
