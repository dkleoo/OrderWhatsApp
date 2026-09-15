package com.example.bakendorderwhatsapp.domain.model

data class IncomingWhatsAppMessage(
    val phoneNumberId: String,
    val displayPhoneNumber: String? = null,
    val from: String,
    val messageId: String,
    val text: String? = null,
    val interactiveReplyId: String? = null,
    val interactiveReplyTitle: String? = null,
    val type: String = "text"
) {
    val isInteractive: Boolean get() = !interactiveReplyId.isNullOrBlank()
}
