package com.devlog.archive.crawler

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import org.xml.sax.InputSource

data class ParsedArticle(
    val title: String,
    val url: String,
    val summary: String?,
    val publishedAt: LocalDateTime?,
)

@Component
class RssFeedParser {

    private val log = LoggerFactory.getLogger(javaClass)

    // 피드 fetch/파싱 실패 시 예외를 그대로 던져 호출자가 FAIL로 기록하게 함
    // 개별 항목 파싱 실패만 스킵 (부분 성공 허용)
    fun parse(rssUrl: String): List<ParsedArticle> {
        val content = fetchContent(rssUrl)
        val sanitized = sanitizeXml(content)
        val feed = SyndFeedInput().build(InputSource(StringReader(sanitized)))
        return feed.entries.mapNotNull { entry ->
            try {
                entry.toParsedArticle()
            } catch (e: Exception) {
                log.warn("항목 파싱 실패: url={}, error={}", rssUrl, e.message)
                null
            }
        }
    }

    private fun fetchContent(rssUrl: String): String {
        val conn = URL(rssUrl).openConnection() as HttpURLConnection
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        conn.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    // XML 1.0에서 허용되지 않는 제어문자 제거 (쿠팡 Medium 피드 대응)
    private fun sanitizeXml(content: String): String =
        content.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")

    private fun SyndEntry.toParsedArticle(): ParsedArticle? {
        val url = link?.takeIf { it.isNotBlank() } ?: return null
        val title = title?.takeIf { it.isNotBlank() } ?: return null

        val summary = (contents.firstOrNull()?.value ?: description?.value)
            ?.let { stripHtml(it) }
            ?.take(500)

        val publishedAt = (publishedDate ?: updatedDate)
            ?.toInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDateTime()

        return ParsedArticle(
            title = title.trim(),
            url = url.trim(),
            summary = summary,
            publishedAt = publishedAt,
        )
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
