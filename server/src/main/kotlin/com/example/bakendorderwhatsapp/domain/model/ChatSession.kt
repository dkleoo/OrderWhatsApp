package com.example.bakendorderwhatsapp.domain.model

import java.util.UUID

data class ChatSession(
    val id: UUID? = null,
    val senderPhone: String,
    val receiverPhone: String,
    val phoneId: String,
    val whatsappBusinessId: String,
    val establishmentId: String = "",
    val establishmentName: String = "",
    val lastActivityAt: Long
)

data class ChatSessionTouchResult(
    val session: ChatSession,
    val isNew: Boolean
)
