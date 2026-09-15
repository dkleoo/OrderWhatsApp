package com.example.bakendorderwhatsapp.domain.usecase

import com.example.bakendorderwhatsapp.domain.model.ChatFlowState
import com.example.bakendorderwhatsapp.domain.model.ChatSession
import com.example.bakendorderwhatsapp.domain.model.ChatSessionTouchResult
import com.example.bakendorderwhatsapp.domain.repository.ChatSessionRepository
import com.example.bakendorderwhatsapp.domain.repository.OrderRepository
import com.example.bakendorderwhatsapp.domain.service.EstablishmentCatalog
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class TouchChatSessionUseCase(
    private val repository: ChatSessionRepository,
    private val orderRepository: OrderRepository,
    private val establishmentCatalog: EstablishmentCatalog,
    private val inactivityMinutes: Long = 5
) {
    private val log = LoggerFactory.getLogger(TouchChatSessionUseCase::class.java)

    suspend operator fun invoke(
        senderPhone: String,
        receiverPhone: String,
        phoneId: String,
        whatsappBusinessId: String,
        establishmentId: String
    ): ChatSessionTouchResult {
        val existing = repository.findBySenderAndPhoneId(senderPhone, phoneId)
        if (existing != null) {
            val cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(inactivityMinutes)
            if (existing.lastActivityAt < cutoff) {
                orderRepository.deleteDraft(senderPhone, phoneId)
                val reset = repository.update(
                    existing.copy(
                        receiverPhone = receiverPhone,
                        whatsappBusinessId = whatsappBusinessId,
                        establishmentId = establishmentId.ifBlank { existing.establishmentId },
                        flowState = ChatFlowState.AWAITING_PRODUCT_NAME,
                        pendingProductId = "",
                        pendingProductName = "",
                        pendingProductPrice = 0.0,
                        pendingProductStock = 0,
                        customerName = "",
                        deliveryAddress = "",
                        paymentMethod = "",
                        lastSearchJson = "",
                        lastActivityAt = System.currentTimeMillis()
                    )
                )
                log.info(
                    "Session expired for sender={}; draft order cleared (completed orders kept)",
                    senderPhone
                )
                return ChatSessionTouchResult(session = reset, isNew = true)
            }

            val refreshed = repository.refreshActivity(
                senderPhone = senderPhone,
                phoneId = phoneId,
                receiverPhone = receiverPhone,
                whatsappBusinessId = whatsappBusinessId,
                lastActivityAt = System.currentTimeMillis()
            ) ?: existing
            return ChatSessionTouchResult(session = refreshed, isNew = false)
        }

        val establishmentName = if (establishmentId.isBlank()) {
            ""
        } else {
            withTimeoutOrNull(8_000) {
                establishmentCatalog.getEstablishmentName(establishmentId)
            }.orEmpty().also { name ->
                if (name.isBlank()) {
                    log.warn("Establishment name unavailable for id={}", establishmentId)
                }
            }
        }

        val created = repository.create(
            ChatSession(
                senderPhone = senderPhone,
                receiverPhone = receiverPhone,
                phoneId = phoneId,
                whatsappBusinessId = whatsappBusinessId,
                establishmentId = establishmentId,
                establishmentName = establishmentName,
                flowState = ChatFlowState.AWAITING_PRODUCT_NAME,
                lastActivityAt = System.currentTimeMillis()
            )
        )
        return ChatSessionTouchResult(session = created, isNew = true)
    }
}
