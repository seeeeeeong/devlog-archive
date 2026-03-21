package com.devlog.archive.article

object ArticleTopicHintExtractor {
    private const val storageDelimiter = "\n"

    private val stopWords = setOf(
        "the", "and", "for", "with", "from", "that", "this", "into", "about", "have", "has",
        "how", "what", "when", "where", "will", "your", "post", "blog", "code", "using", "use",
        "guide", "story", "engineering", "system", "service", "platform", "team", "tech",
        "에서", "으로", "하다", "하는", "했다", "대한", "관련", "정리", "구현", "문제", "해결", "개발", "적용",
        "기능", "구조", "설계", "이슈", "트러블", "슈팅", "사용", "방법", "이렇게", "이유", "운영기", "구축기",
    )

    private val keywordDisplayMap = mapOf(
        "aws" to "AWS",
        "ec2" to "EC2",
        "s3" to "S3",
        "rds" to "RDS",
        "redis" to "Redis",
        "kafka" to "Kafka",
        "postgresql" to "PostgreSQL",
        "postgres" to "PostgreSQL",
        "mysql" to "MySQL",
        "oauth" to "OAuth",
        "jwt" to "JWT",
        "grafana" to "Grafana",
        "prometheus" to "Prometheus",
        "terraform" to "Terraform",
        "docker" to "Docker",
        "kubernetes" to "Kubernetes",
        "grpc" to "gRPC",
        "mview" to "Materialized View",
        "llm" to "LLM",
    )

    private val canonicalTopics = linkedMapOf(
        "Redis Lua Script" to listOf("redis lua", "lua script"),
        "Redis Cache" to listOf("redis cache", "cache stampede"),
        "Redis" to listOf("redis"),
        "Kafka Consumer" to listOf("kafka consumer", "consumer", "컨슈머"),
        "Kafka" to listOf("kafka"),
        "Outbox Pattern" to listOf("outbox pattern", "outbox", "아웃박스"),
        "Idempotency" to listOf("idempotency", "deduplication", "idemponent", "멱등성"),
        "Materialized View" to listOf("materialized view", "mview"),
        "Terraform" to listOf("terraform", "테라폼"),
        "PostgreSQL" to listOf("postgresql", "postgres"),
        "Coupon System" to listOf("coupon system", "coupon", "쿠폰 시스템", "쿠폰"),
        "Failure Handling" to listOf("failure handling", "fault tolerance", "resilience", "fallback", "장애", "격리"),
        "Performance" to listOf("performance", "latency", "throughput", "성능"),
        "Prometheus" to listOf("prometheus"),
        "Grafana" to listOf("grafana"),
        "Circuit Breaker" to listOf("circuit breaker"),
        "CloudFront" to listOf("cloudfront"),
        "Docker" to listOf("docker"),
        "Kubernetes" to listOf("kubernetes", "k8s"),
        "AWS" to listOf("aws"),
        "t4g.micro" to listOf("t4g.micro", "t4g micro"),
    )

    fun extract(title: String, summary: String?): List<String> {
        val normalizedTitle = normalizeText(title)
        val normalizedSummary = normalizeText(summary.orEmpty())
        val combined = listOf(normalizedTitle, normalizedSummary)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        if (combined.isBlank()) {
            return emptyList()
        }

        val hints = linkedSetOf<String>()

        canonicalTopics.forEach { (topic, aliases) ->
            if (aliases.any { alias -> alias in combined }) {
                hints += topic
            }
        }

        extractTitlePhrases(title).forEach { hints += it }

        val scoredTokens = sequenceOf(
            normalizedTitle.splitToSequence(" ").map { it to 3 },
            normalizedSummary.splitToSequence(" ").map { it to 1 },
        )
            .flatten()
            .filter { (token, _) -> token.length >= 3 && token.any(Char::isLetterOrDigit) }
            .filterNot { (token, _) -> token in stopWords }
            .groupingBy { it.first }
            .fold(0) { acc, entry -> acc + entry.second }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key.length })
            .map { toDisplayText(it.key) }
            .filter { it.length >= 3 }
            .take(4)

        scoredTokens.forEach { hints += it }

        return hints.take(6)
    }

    fun toStorageValue(topicHints: List<String>): String? {
        val normalized = topicHints.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
            .toList()
        return normalized.takeIf { it.isNotEmpty() }?.joinToString(storageDelimiter)
    }

    fun fromStorageValue(topicHints: String?): List<String> {
        return topicHints.orEmpty()
            .split(storageDelimiter)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    fun buildEmbeddingText(title: String, summary: String?, topicHints: List<String>): String {
        return buildList {
            add(title)
            add(title)
            if (topicHints.isNotEmpty()) {
                add(topicHints.joinToString(" "))
            }
            summary?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
            .joinToString(" ")
            .trim()
    }

    private fun extractTitlePhrases(title: String): List<String> {
        return title.split(Regex("[:|/,-]"))
            .asSequence()
            .map { normalizeText(it) }
            .map { phrase ->
                phrase.split(" ")
                    .filter { it.length >= 2 && it !in stopWords }
                    .joinToString(" ")
                    .trim()
            }
            .filter { it.length >= 6 }
            .filter { it.count { ch -> ch == ' ' } in 0..4 }
            .distinct()
            .map { phrase ->
                phrase.split(" ")
                    .joinToString(" ") { token -> toDisplayText(token) }
            }
            .take(3)
            .toList()
    }

    private fun toDisplayText(token: String): String {
        return keywordDisplayMap[token]
            ?: token.split(".")
                .joinToString(".") { part -> part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
    }

    private fun normalizeText(text: String): String {
        return text.lowercase()
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("`[^`]*`"), " ")
            .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("[*_>#()]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
