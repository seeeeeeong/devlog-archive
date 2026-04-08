package com.devlog.archive.domain.similar.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "similar_clicks")
class SimilarClickEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val articleId: Long,
    val sourceTitle: String? = null,
    val position: Short? = null,
    val totalResults: Short? = null,
    val rrfScore: Double? = null,
    val stage: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
