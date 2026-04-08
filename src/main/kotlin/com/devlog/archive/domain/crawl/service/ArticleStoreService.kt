package com.devlog.archive.domain.crawl.service

import com.devlog.archive.domain.article.entity.ArticleEntity
import com.devlog.archive.domain.article.repository.ArticleRepository
import com.devlog.archive.domain.article.service.ArticleTopicHintExtractor
import com.devlog.archive.domain.blog.entity.BlogEntity
import com.devlog.archive.domain.crawl.log.entity.CrawlLogEntity
import com.devlog.archive.domain.crawl.log.repository.CrawlLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime

@Service
class ArticleStoreService(
    private val articleRepository: ArticleRepository,
    private val crawlLogRepository: CrawlLogRepository,
) {
    @Transactional
    fun startLog(blog: BlogEntity): CrawlLogEntity =
        crawlLogRepository.save(CrawlLogEntity(blog = blog, startedAt = LocalDateTime.now(), status = "IN_PROGRESS"))

    @Transactional
    fun saveArticleIfNew(blog: BlogEntity, parsed: ParsedArticle): ArticleEntity? {
        val urlHash = sha256(parsed.url)
        if (articleRepository.existsByUrlHash(urlHash)) return null
        val topicHints = ArticleTopicHintExtractor.toStorageValue(
            ArticleTopicHintExtractor.extract(parsed.title, parsed.summary)
        )
        return articleRepository.save(
            ArticleEntity(
                blog = blog,
                title = parsed.title,
                url = parsed.url,
                urlHash = urlHash,
                summary = parsed.summary,
                topicHints = topicHints,
                publishedAt = parsed.publishedAt,
            )
        )
    }

    @Transactional
    fun finishLog(log: CrawlLogEntity, newCount: Int, status: String, message: String?) {
        log.finishedAt = LocalDateTime.now()
        log.newCount = newCount
        log.status = status
        log.message = message
        crawlLogRepository.save(log)
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
