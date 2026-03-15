package com.devlog.archive.storage

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "blogs")
class BlogEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val name: String,
    val company: String,
    val rssUrl: String,
    val homeUrl: String,
    val active: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
