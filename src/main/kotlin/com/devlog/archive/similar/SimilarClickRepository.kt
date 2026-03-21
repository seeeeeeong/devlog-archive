package com.devlog.archive.similar

import org.springframework.data.jpa.repository.JpaRepository

interface SimilarClickRepository : JpaRepository<SimilarClickEntity, Long>
