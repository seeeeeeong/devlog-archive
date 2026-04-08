package com.devlog.archive.domain.similar.service

import com.devlog.archive.domain.similar.entity.SimilarClickEntity
import com.devlog.archive.domain.similar.repository.SimilarClickRepository
import com.devlog.archive.domain.similar.service.dto.SimilarClickCommand
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SimilarClickService(
    private val similarClickRepository: SimilarClickRepository,
) {
    @Transactional
    fun record(command: SimilarClickCommand) {
        similarClickRepository.save(
            SimilarClickEntity(
                articleId = command.articleId,
                sourceTitle = command.sourceTitle,
                position = command.position,
                totalResults = command.totalResults,
                rrfScore = command.rrfScore,
                stage = command.stage,
            )
        )
    }
}
