package com.devlog.archive

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableCaching
@EnableRetry
@ConfigurationPropertiesScan("com.devlog.archive.config")
class DevlogArchiveApplication

fun main(args: Array<String>) {
    runApplication<DevlogArchiveApplication>(*args)
}
