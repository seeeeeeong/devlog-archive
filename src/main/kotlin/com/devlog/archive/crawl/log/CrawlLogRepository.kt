package com.devlog.archive.crawl.log

import org.springframework.data.jpa.repository.JpaRepository

interface CrawlLogRepository : JpaRepository<CrawlLogEntity, Long>
