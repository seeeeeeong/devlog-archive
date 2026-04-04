package com.devlog.archive.article

import com.devlog.archive.common.StopWords

object ArticleTopicHintExtractor {
    private const val storageDelimiter = "\n"

    private val stopWords = StopWords.set

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
        "pgvector" to "pgvector",
        "embedding" to "Embedding",
        "openai" to "OpenAI",
        "langchain" to "LangChain",
        "rag" to "RAG",
        "jpa" to "JPA",
        "hibernate" to "Hibernate",
        "flyway" to "Flyway",
        "nginx" to "Nginx",
        "caddy" to "Caddy",
    )

    private val canonicalTopics = linkedMapOf(
        "Redis Lua Script" to listOf("redis lua", "lua script"),
        "Redis Cache" to listOf("redis cache", "cache stampede"),
        "Redis" to listOf("redis"),
        "Kafka Consumer" to listOf("kafka consumer", "컨슈머"),
        "Kafka Streams" to listOf("kafka streams"),
        "Kafka" to listOf("kafka"),
        "Outbox Pattern" to listOf("outbox pattern", "outbox", "아웃박스"),
        "Idempotency" to listOf("idempotency", "deduplication", "idemponent", "멱등성"),
        "Materialized View" to listOf("materialized view", "mview"),
        "Terraform" to listOf("terraform", "테라폼"),
        "PostgreSQL" to listOf("postgresql", "postgres"),
        "MySQL" to listOf("mysql"),
        "MongoDB" to listOf("mongodb", "몽고"),
        "Elasticsearch" to listOf("elasticsearch", "elastic search"),
        "DynamoDB" to listOf("dynamodb"),
        "Coupon System" to listOf("coupon system", "coupon", "쿠폰 시스템", "쿠폰"),
        "Failure Handling" to listOf("failure handling", "fault tolerance", "resilience", "fallback", "장애 격리"),
        "Performance" to listOf("performance", "latency", "throughput", "성능"),
        "Prometheus" to listOf("prometheus"),
        "Grafana" to listOf("grafana"),
        "Circuit Breaker" to listOf("circuit breaker"),
        "Spring Boot" to listOf("spring boot", "springboot"),
        "Spring" to listOf("spring"),
        "React" to listOf("react"),
        "TypeScript" to listOf("typescript"),
        "Go" to listOf("golang"),
        "gRPC" to listOf("grpc"),
        "GraphQL" to listOf("graphql"),
        "REST API" to listOf("rest api", "restful"),
        "Microservices" to listOf("microservice", "마이크로서비스"),
        "Event-Driven" to listOf("event-driven", "event driven", "이벤트 기반"),
        "CQRS" to listOf("cqrs"),
        "CI/CD" to listOf("ci/cd", "cicd"),
        "GitHub Actions" to listOf("github actions"),
        "CloudFront" to listOf("cloudfront"),
        "Docker" to listOf("docker"),
        "Kubernetes" to listOf("kubernetes", "k8s"),
        "AWS" to listOf("aws"),
        "t4g.micro" to listOf("t4g.micro", "t4g micro"),
        "Monitoring" to listOf("monitoring", "observability", "모니터링"),
        "Testing" to listOf("tdd", "unit test", "테스트"),
        "pgvector" to listOf("pgvector", "pg_vector"),
        "Vector Search" to listOf("vector search", "벡터 검색", "벡터 서치", "similarity search"),
        "Embedding" to listOf("embedding", "임베딩"),
        "Cosine Similarity" to listOf("cosine similarity", "cosine distance", "코사인 유사도"),
        "Recommendation" to listOf("recommendation", "추천 시스템", "추천 엔진"),
        "Full-Text Search" to listOf("full-text search", "full text search", "전문 검색"),
        "RAG" to listOf("retrieval augmented", "retrieval-augmented"),
        "LangChain" to listOf("langchain"),
        "OpenAI" to listOf("openai"),
        "JPA" to listOf("jpa", "hibernate"),
        "Transaction" to listOf("transaction", "트랜잭션"),
        "Index" to listOf("index tuning", "인덱스 튜닝"),
        "Caching" to listOf("caching", "캐싱", "cache invalidation"),
        "Load Balancing" to listOf("load balancing", "로드밸런싱"),
        "API Gateway" to listOf("api gateway"),
        "Batch Processing" to listOf("batch processing", "배치 처리"),
        "Flyway" to listOf("flyway"),
        "Nginx" to listOf("nginx"),
        "Caddy" to listOf("caddy"),
        "OpenSearch" to listOf("opensearch"),
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
            if (aliases.any { alias -> containsWord(combined, alias) }) {
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
            add("Title: $title")
            if (topicHints.isNotEmpty()) {
                add("Topics: ${topicHints.joinToString(", ")}")
            }
            summary?.takeIf { it.isNotBlank() }?.let { add("Summary: ${normalizeText(it)}") }
        }
            .joinToString("\n")
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

    private fun containsWord(text: String, word: String): Boolean {
        val pattern = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(word)}(?![\\p{L}\\p{N}])")
        return pattern.containsMatchIn(text)
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
