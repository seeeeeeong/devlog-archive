package com.devlog.archive.domain.similar.repository

import org.springframework.data.jpa.repository.JpaRepository
import com.devlog.archive.domain.similar.entity.SimilarClickEntity

interface SimilarClickRepository : JpaRepository<SimilarClickEntity, Long>
