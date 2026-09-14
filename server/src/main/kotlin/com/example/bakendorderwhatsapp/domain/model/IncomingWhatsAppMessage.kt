package com.example.bakendorderwhatsapp.domain.model

data class IncomingWhatsAppMessage(
    val phoneNumberId: String,
    val from: String,
    val messageId: String,
    val text: String?
)
