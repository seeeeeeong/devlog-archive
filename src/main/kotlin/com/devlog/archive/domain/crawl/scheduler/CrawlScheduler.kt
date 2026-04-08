package com.devlog.archive.domain.crawl.scheduler

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import com.devlog.archive.domain.crawl.service.CrawlService

@Component
class CrawlScheduler(
    private val crawlService: CrawlService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 3 * * MON")  // 매주 월요일 새벽 3시
    fun scheduleCrawl() {
        log.info("정기 크롤링 시작")
        crawlService.crawlAll()
        log.info("정기 크롤링 종료")
    }
}
