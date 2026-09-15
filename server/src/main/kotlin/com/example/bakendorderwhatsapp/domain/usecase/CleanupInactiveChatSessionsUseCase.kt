package com.example.bakendorderwhatsapp.domain.usecase

import com.example.bakendorderwhatsapp.domain.repository.ChatSessionRepository
import com.example.bakendorderwhatsapp.domain.repository.OrderRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class CleanupInactiveChatSessionsUseCase(
    private val chatSessionRepository: ChatSessionRepository,
    private val orderRepository: OrderRepository,
    private val inactivityMinutes: Long = 5
) {
    private val log = LoggerFactory.getLogger(CleanupInactiveChatSessionsUseCase::class.java)

    suspend operator fun invoke(): Int {
        val cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(inactivityMinutes)
        val inactive = chatSessionRepository.findInactive(cutoff)
        var clearedDrafts = 0
        inactive.forEach { session ->
            // Solo borra pedidos DRAFT (incompletos). Los COMPLETED se conservan.
            clearedDrafts += orderRepository.deleteDraft(
                senderPhone = session.senderPhone,
                phoneId = session.phoneId
            )
        }
        val deleted = chatSessionRepository.deleteInactive(cutoff)
        if (deleted > 0 || clearedDrafts > 0) {
            log.info(
                "Expired {} chat session(s); cleared {} draft order(s) (completed orders kept)",
                deleted,
                clearedDrafts
            )
        }
        return deleted
    }
}
