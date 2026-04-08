package com.devlog.archive.domain.article.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ArticleTopicHintExtractorTest {

    @Test
    fun `extracts canonical topic hints from article title and summary`() {
        val hints = ArticleTopicHintExtractor.extract(
            title = "Redis Lua Script로 선착순 쿠폰 발급 제어하기",
            summary = "쿠폰 시스템에서 redis cache와 lua script를 이용해 동시성 문제를 제어한 글",
        )

        assertThat(hints).contains("Redis Lua Script", "Coupon System", "Redis")
    }

    @Test
    fun `serializes and deserializes topic hints`() {
        val stored = ArticleTopicHintExtractor.toStorageValue(listOf("Redis", "Kafka Consumer", "Redis"))

        assertThat(stored).isEqualTo("Redis\nKafka Consumer")
        assertThat(ArticleTopicHintExtractor.fromStorageValue(stored)).containsExactly("Redis", "Kafka Consumer")
    }
}
