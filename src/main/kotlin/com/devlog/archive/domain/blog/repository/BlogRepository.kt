package com.devlog.archive.domain.blog.repository

import org.springframework.data.jpa.repository.JpaRepository
import com.devlog.archive.domain.blog.entity.BlogEntity

interface BlogRepository : JpaRepository<BlogEntity, Long> {
    fun findAllByActiveTrue(): List<BlogEntity>
}
