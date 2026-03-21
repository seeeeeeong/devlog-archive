package com.devlog.archive.similar

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "similar_clicks")
class SimilarClickEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val articleId: Long,
    val sourceTitle: String? = null,
    val stage: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
