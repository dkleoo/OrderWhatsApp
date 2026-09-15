package com.example.bakendorderwhatsapp.domain.usecase

import com.example.bakendorderwhatsapp.domain.repository.ChatSessionRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class CleanupInactiveChatSessionsUseCase(
    private val repository: ChatSessionRepository,
    private val inactivityMinutes: Long = 5
) {
    private val log = LoggerFactory.getLogger(CleanupInactiveChatSessionsUseCase::class.java)

    suspend operator fun invoke(): Int {
        val cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(inactivityMinutes)
        val deleted = repository.deleteInactive(cutoff)
        if (deleted > 0) {
            log.info("Deleted {} inactive chat session(s) older than {} minutes", deleted, inactivityMinutes)
        }
        return deleted
    }
}
