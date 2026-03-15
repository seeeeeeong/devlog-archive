package com.devlog.archive.storage

import org.springframework.data.jpa.repository.JpaRepository

interface CrawlLogRepository : JpaRepository<CrawlLogEntity, Long>
