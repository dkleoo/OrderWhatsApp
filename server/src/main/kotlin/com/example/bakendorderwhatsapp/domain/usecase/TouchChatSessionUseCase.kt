package com.example.bakendorderwhatsapp.domain.usecase

import com.example.bakendorderwhatsapp.domain.model.ChatSession
import com.example.bakendorderwhatsapp.domain.model.ChatSessionTouchResult
import com.example.bakendorderwhatsapp.domain.repository.ChatSessionRepository
import com.example.bakendorderwhatsapp.domain.service.EstablishmentCatalog
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

class TouchChatSessionUseCase(
    private val repository: ChatSessionRepository,
    private val establishmentCatalog: EstablishmentCatalog
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
            val refreshed = repository.refreshActivity(
                senderPhone = senderPhone,
                phoneId = phoneId,
                receiverPhone = receiverPhone,
                whatsappBusinessId = whatsappBusinessId,
                lastActivityAt = System.currentTimeMillis()
            ) ?: existing
            return ChatSessionTouchResult(session = refreshed, isNew = false)
        }

        // Solo al inicio de sesión; no bloquea más de 8s si el API de establishment falla/lento.
        val establishmentName = if (establishmentId.isBlank()) {
            ""
        } else {
            withTimeoutOrNull(8_000) {
                establishmentCatalog.getEstablishmentName(establishmentId)
            }.orEmpty().also { name ->
                if (name.isBlank()) {
                    log.warn("Establishment name unavailable for id={}", establishmentId)
                } else {
                    log.info("Loaded establishment name='{}' for id={}", name, establishmentId)
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
                lastActivityAt = System.currentTimeMillis()
            )
        )
        return ChatSessionTouchResult(session = created, isNew = true)
    }
}
