package com.devlog.archive.domain.article.entity

import com.devlog.archive.domain.blog.entity.BlogEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "articles")
class ArticleEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blog_id")
    val blog: BlogEntity,

    val title: String,
    val url: String,
    val urlHash: String,

    @Column(columnDefinition = "TEXT")
    val summary: String?,

    @Column(name = "topic_hints", columnDefinition = "TEXT")
    val topicHints: String? = null,

    val publishedAt: LocalDateTime?,
    val crawledAt: LocalDateTime = LocalDateTime.now(),
)
