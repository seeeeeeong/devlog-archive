package com.devlog.archive.domain.crawl.log.repository

import org.springframework.data.jpa.repository.JpaRepository
import com.devlog.archive.domain.crawl.log.entity.CrawlLogEntity

interface CrawlLogRepository : JpaRepository<CrawlLogEntity, Long>
