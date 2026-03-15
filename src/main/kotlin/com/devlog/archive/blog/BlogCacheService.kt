package com.devlog.archive.blog

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class BlogCacheService(private val blogRepository: BlogRepository) {

    @Cacheable("blogs")
    fun findAll(): List<BlogEntity> = blogRepository.findAll()
}
