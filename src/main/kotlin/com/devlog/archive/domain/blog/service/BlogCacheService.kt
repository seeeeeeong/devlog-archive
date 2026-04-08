package com.devlog.archive.domain.blog.service

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import com.devlog.archive.domain.blog.entity.BlogEntity
import com.devlog.archive.domain.blog.repository.BlogRepository

@Service
class BlogCacheService(private val blogRepository: BlogRepository) {

    @Cacheable("blogs")
    fun findAll(): List<BlogEntity> = blogRepository.findAll()
}
