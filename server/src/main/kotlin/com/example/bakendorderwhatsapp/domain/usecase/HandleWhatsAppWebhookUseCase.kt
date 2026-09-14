package com.example.bakendorderwhatsapp.domain.usecase

import com.example.bakendorderwhatsapp.domain.model.IncomingWhatsAppMessage
import com.example.bakendorderwhatsapp.domain.repository.WhatsAppSettingsRepository
import com.example.bakendorderwhatsapp.domain.service.WhatsAppMessageSender
import org.slf4j.LoggerFactory

class HandleWhatsAppWebhookUseCase(
    private val repository: WhatsAppSettingsRepository,
    private val messageSender: WhatsAppMessageSender,
    private val autoReplyBody: String = "What can I help you with today?"
) {
    private val log = LoggerFactory.getLogger(HandleWhatsAppWebhookUseCase::class.java)

    suspend operator fun invoke(messages: List<IncomingWhatsAppMessage>) {
        messages.forEach { message ->
            val settings = repository.getByPhoneId(message.phoneNumberId)
            if (settings == null) {
                log.warn("No WhatsApp settings found for phoneId={}", message.phoneNumberId)
                return@forEach
            }
            if (settings.token.isBlank()) {
                log.warn("Empty token for phoneId={}", message.phoneNumberId)
                return@forEach
            }

            messageSender.sendTextMessage(
                phoneNumberId = settings.phoneId.ifBlank { message.phoneNumberId },
                accessToken = settings.token,
                to = message.from,
                body = autoReplyBody
            )
        }
    }
}
