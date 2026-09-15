package com.example.bakendorderwhatsapp.domain.usecase

import com.example.bakendorderwhatsapp.domain.model.IncomingWhatsAppMessage
import com.example.bakendorderwhatsapp.domain.repository.WhatsAppSettingsRepository
import com.example.bakendorderwhatsapp.domain.service.PosAssistantAi
import com.example.bakendorderwhatsapp.domain.service.WhatsAppMessageSender
import org.slf4j.LoggerFactory

class HandleWhatsAppWebhookUseCase(
    private val repository: WhatsAppSettingsRepository,
    private val messageSender: WhatsAppMessageSender,
    private val posAssistantAi: PosAssistantAi,
    private val touchChatSession: TouchChatSessionUseCase,
    private val fallbackReply: String = "No pude procesar tu mensaje ahora. Intenta de nuevo."
) {
    private val log = LoggerFactory.getLogger(HandleWhatsAppWebhookUseCase::class.java)

    suspend operator fun invoke(messages: List<IncomingWhatsAppMessage>) {
        log.info("Handling {} WhatsApp message(s)", messages.size)

        messages.forEach { message ->
            try {
                processMessage(message)
            } catch (e: Exception) {
                log.error("Unhandled error processing message from={}", message.from, e)
            }
        }
    }

    private suspend fun processMessage(message: IncomingWhatsAppMessage) {
        val settings = repository.getByPhoneId(message.phoneNumberId)
        if (settings == null) {
            log.warn("No WhatsApp settings found for phoneId={}", message.phoneNumberId)
            return
        }
        if (settings.token.isBlank()) {
            log.warn("Empty token for phoneId={}", message.phoneNumberId)
            return
        }

        val receiverPhone = settings.whatsappPhone
            .ifBlank { message.displayPhoneNumber.orEmpty() }

        val sessionResult = runCatching {
            touchChatSession(
                senderPhone = message.from,
                receiverPhone = receiverPhone,
                phoneId = settings.phoneId.ifBlank { message.phoneNumberId },
                whatsappBusinessId = settings.whatsappBusinessId,
                establishmentId = settings.establishmentId
            )
        }.getOrElse {
            log.error("Failed to touch chat session for from={}", message.from, it)
            null
        }

        val storeName = sessionResult?.session?.establishmentName
            ?.ifBlank { settings.establishmentName }
            ?: settings.establishmentName
        val isNewSession = sessionResult?.isNew == true

        val userText = message.text?.trim().orEmpty().ifBlank { "(mensaje vacío)" }
        log.info(
            "Asking AI from={} store='{}' newSession={} text='{}'",
            message.from,
            storeName,
            isNewSession,
            userText.take(80)
        )

        val aiReply = runCatching {
            posAssistantAi.reply(
                userMessage = userText,
                storeName = storeName,
                isNewSession = isNewSession
            )
        }.getOrElse { error ->
            log.error("AI failed for from={}", message.from, error)
            fallbackReply
        }

        runCatching {
            messageSender.sendTextMessage(
                phoneNumberId = settings.phoneId.ifBlank { message.phoneNumberId },
                accessToken = settings.token,
                to = message.from,
                body = aiReply
            )
        }.onFailure {
            log.error("Failed sending WhatsApp reply to {}", message.from, it)
        }
    }
}
