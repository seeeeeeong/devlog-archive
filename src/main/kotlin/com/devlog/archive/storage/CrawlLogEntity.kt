package com.devlog.archive.storage

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "crawl_logs")
class CrawlLogEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blog_id")
    val blog: BlogEntity,

    val startedAt: LocalDateTime,
    var finishedAt: LocalDateTime? = null,
    var newCount: Int = 0,
    var status: String,    // SUCCESS / PARTIAL / FAIL
    var message: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
