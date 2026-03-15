package com.devlog.archive.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "openai")
data class OpenAiProperties(
    val apiKey: String,
    val embeddingModel: String = "text-embedding-3-small",
    val baseUrl: String = "https://api.openai.com",
)
