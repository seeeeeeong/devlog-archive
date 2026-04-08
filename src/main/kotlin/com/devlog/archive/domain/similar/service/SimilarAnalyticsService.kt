package com.devlog.archive.domain.similar.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class SimilarAnalyticsService(private val jdbcTemplate: JdbcTemplate) {

    fun getClickSummary(days: Int = 30): Map<String, Any> {
        val totalClicks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM similar_clicks WHERE created_at >= now() - INTERVAL '1 day' * ?",
            Long::class.java,
            days,
        ) ?: 0L

        val positionDistribution = jdbcTemplate.queryForList(
            """
            SELECT position, COUNT(*) AS cnt
            FROM similar_clicks
            WHERE position IS NOT NULL AND created_at >= now() - INTERVAL '1 day' * ?
            GROUP BY position
            ORDER BY position
            """.trimIndent(),
            days,
        ).associate { (it["position"] as Number).toInt() to (it["cnt"] as Number).toLong() }

        val topClickedArticles = jdbcTemplate.queryForList(
            """
            SELECT sc.article_id, a.title, COUNT(*) AS clicks
            FROM similar_clicks sc
            JOIN articles a ON a.id = sc.article_id
            WHERE sc.created_at >= now() - INTERVAL '1 day' * ?
            GROUP BY sc.article_id, a.title
            ORDER BY clicks DESC
            LIMIT 10
            """.trimIndent(),
            days,
        ).map {
            mapOf(
                "articleId" to it["article_id"],
                "title" to it["title"],
                "clicks" to it["clicks"],
            )
        }

        val avgScoreByPosition = jdbcTemplate.queryForList(
            """
            SELECT position, ROUND(AVG(rrf_score)::numeric, 4) AS avg_score
            FROM similar_clicks
            WHERE position IS NOT NULL AND rrf_score IS NOT NULL
              AND created_at >= now() - INTERVAL '1 day' * ?
            GROUP BY position
            ORDER BY position
            """.trimIndent(),
            days,
        ).associate { (it["position"] as Number).toInt() to it["avg_score"] }

        val topSourcePages = jdbcTemplate.queryForList(
            """
            SELECT source_title, COUNT(*) AS clicks
            FROM similar_clicks
            WHERE source_title IS NOT NULL AND created_at >= now() - INTERVAL '1 day' * ?
            GROUP BY source_title
            ORDER BY clicks DESC
            LIMIT 10
            """.trimIndent(),
            days,
        ).map {
            mapOf(
                "sourceTitle" to it["source_title"],
                "clicks" to it["clicks"],
            )
        }

        return mapOf(
            "days" to days,
            "totalClicks" to totalClicks,
            "positionDistribution" to positionDistribution,
            "avgRrfScoreByPosition" to avgScoreByPosition,
            "topClickedArticles" to topClickedArticles,
            "topSourcePages" to topSourcePages,
        )
    }
}
