package com.example.bakendorderwhatsapp.presentation.dto

import com.example.bakendorderwhatsapp.domain.model.IncomingWhatsAppMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WhatsAppWebhookPayloadDto(
    val `object`: String? = null,
    val entry: List<WebhookEntryDto> = emptyList()
)

@Serializable
data class WebhookEntryDto(
    val id: String? = null,
    val changes: List<WebhookChangeDto> = emptyList()
)

@Serializable
data class WebhookChangeDto(
    val field: String? = null,
    val value: WebhookValueDto? = null
)

@Serializable
data class WebhookValueDto(
    @SerialName("messaging_product")
    val messagingProduct: String? = null,
    val metadata: WebhookMetadataDto? = null,
    val messages: List<WebhookMessageDto> = emptyList()
)

@Serializable
data class WebhookMetadataDto(
    @SerialName("display_phone_number")
    val displayPhoneNumber: String? = null,
    @SerialName("phone_number_id")
    val phoneNumberId: String? = null
)

@Serializable
data class WebhookMessageDto(
    val from: String? = null,
    val id: String? = null,
    val timestamp: String? = null,
    val type: String? = null,
    val text: WebhookTextDto? = null
)

@Serializable
data class WebhookTextDto(
    val body: String? = null
)

fun WhatsAppWebhookPayloadDto.toIncomingMessages(): List<IncomingWhatsAppMessage> {
    return entry.flatMap { entry ->
        entry.changes
            .filter { it.field == "messages" }
            .mapNotNull { it.value }
            .flatMap { value ->
                val phoneNumberId = value.metadata?.phoneNumberId.orEmpty()
                val displayPhoneNumber = value.metadata?.displayPhoneNumber
                value.messages
                    .filter { it.type == "text" || it.text != null }
                    .mapNotNull { msg ->
                        val from = msg.from ?: return@mapNotNull null
                        val messageId = msg.id ?: return@mapNotNull null
                        if (phoneNumberId.isBlank()) return@mapNotNull null
                        IncomingWhatsAppMessage(
                            phoneNumberId = phoneNumberId,
                            displayPhoneNumber = displayPhoneNumber,
                            from = from,
                            messageId = messageId,
                            text = msg.text?.body
                        )
                    }
            }
    }
}
