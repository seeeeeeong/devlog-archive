package com.devlog.archive.storage

import org.springframework.data.jpa.repository.JpaRepository

interface BlogRepository : JpaRepository<BlogEntity, Long> {
    fun findAllByActiveTrue(): List<BlogEntity>
}
