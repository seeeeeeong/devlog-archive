package com.devlog.archive.crawl

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ContentScraper {
    private val log = LoggerFactory.getLogger(javaClass)

    private val contentSelectors = listOf(
        "article",
        "[role=main]",
        "main",
        ".post-content",
        ".entry-content",
        ".article-content",
        ".content",
    )

    fun scrape(url: String): String? {
        return try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(8_000)
                .followRedirects(true)
                .get()

            extractContent(doc)
        } catch (e: Exception) {
            log.debug("본문 스크래핑 실패: url={}, error={}", url, e.message)
            null
        }
    }

    private val boilerplateSelectors = listOf(
        "nav", "footer", "header", "aside",
        ".sidebar", ".comments", ".navigation", ".menu", ".footer", ".header",
        "[role=navigation]", "[role=banner]", "[role=contentinfo]",
        "script", "style", "noscript",
    )

    private fun extractContent(doc: Document): String? {
        boilerplateSelectors.forEach { selector ->
            doc.select(selector).remove()
        }

        for (selector in contentSelectors) {
            val element = doc.selectFirst(selector) ?: continue
            val text = element.text()
                .replace(Regex("\\s+"), " ")
                .trim()
            if (text.length >= 100) {
                return text.take(3000)
            }
        }

        val text = doc.body().text()
            .replace(Regex("\\s+"), " ")
            .trim()
        return text.takeIf { it.length >= 100 }?.take(3000)
    }
}
