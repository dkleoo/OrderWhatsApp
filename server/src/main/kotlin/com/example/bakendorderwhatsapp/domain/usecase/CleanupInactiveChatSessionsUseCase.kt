package com.example.bakendorderwhatsapp.domain.usecase

import com.example.bakendorderwhatsapp.domain.repository.CartItemRepository
import com.example.bakendorderwhatsapp.domain.repository.ChatSessionRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class CleanupInactiveChatSessionsUseCase(
    private val chatSessionRepository: ChatSessionRepository,
    private val cartItemRepository: CartItemRepository,
    private val inactivityMinutes: Long = 5
) {
    private val log = LoggerFactory.getLogger(CleanupInactiveChatSessionsUseCase::class.java)

    suspend operator fun invoke(): Int {
        val cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(inactivityMinutes)
        val inactive = chatSessionRepository.findInactive(cutoff)
        var clearedCarts = 0
        inactive.forEach { session ->
            clearedCarts += cartItemRepository.deleteBySenderAndPhoneId(
                senderPhone = session.senderPhone,
                phoneId = session.phoneId
            )
        }
        val deleted = chatSessionRepository.deleteInactive(cutoff)
        if (deleted > 0 || clearedCarts > 0) {
            log.info(
                "Expired {} chat session(s); cleared {} cart item(s) (inactivity > {} min)",
                deleted,
                clearedCarts,
                inactivityMinutes
            )
        }
        return deleted
    }
}
