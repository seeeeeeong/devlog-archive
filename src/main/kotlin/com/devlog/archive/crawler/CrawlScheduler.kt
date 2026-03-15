package com.devlog.archive.crawler

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CrawlScheduler(
    private val crawlService: CrawlService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 3_600_000)  // 1시간
    fun scheduleCrawl() {
        log.info("정기 크롤링 시작")
        crawlService.crawlAll()
        log.info("정기 크롤링 종료")
    }
}
