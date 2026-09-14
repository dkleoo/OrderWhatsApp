package com.example.bakendorderwhatsapp.domain.service

interface WhatsAppMessageSender {
    suspend fun sendTextMessage(
        phoneNumberId: String,
        accessToken: String,
        to: String,
        body: String
    )
}
