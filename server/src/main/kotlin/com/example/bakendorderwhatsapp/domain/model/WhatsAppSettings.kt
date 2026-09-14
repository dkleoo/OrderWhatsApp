package com.example.bakendorderwhatsapp.domain.model

import java.util.UUID

data class WhatsAppSettings(
    val id: UUID? = null,
    val businessId: String,
    val establishmentId: String,
    val establishmentName: String,
    val whatsappPhone: String,
    val phoneId: String,
    val whatsappBusinessId: String,
    val token: String
)
