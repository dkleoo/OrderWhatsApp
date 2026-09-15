package com.example.bakendorderwhatsapp.domain.usecase

import com.example.bakendorderwhatsapp.domain.model.ChatSession
import com.example.bakendorderwhatsapp.domain.model.ChatSessionTouchResult
import com.example.bakendorderwhatsapp.domain.repository.ChatSessionRepository
import com.example.bakendorderwhatsapp.domain.service.EstablishmentCatalog

class TouchChatSessionUseCase(
    private val repository: ChatSessionRepository,
    private val establishmentCatalog: EstablishmentCatalog
) {
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

        // Solo al inicio de una sesión activa se consulta el establishment.
        val establishmentName = establishmentCatalog.getEstablishmentName(establishmentId)
            ?: ""

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
