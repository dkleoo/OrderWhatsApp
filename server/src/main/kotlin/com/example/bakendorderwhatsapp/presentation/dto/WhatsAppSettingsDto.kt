package com.example.bakendorderwhatsapp.presentation.dto

import com.example.bakendorderwhatsapp.domain.model.WhatsAppSettings
import kotlinx.serialization.Serializable

@Serializable
data class WhatsAppSettingsDto(
    val businessId: String = "",
    val establishmentId: String = "",
    val establishmentName: String = "",
    val whatsappPhone: String = "",
    val phoneId: String = "",
    val whatsappBusinessId: String = "",
    val token: String = ""
)

fun WhatsAppSettingsDto.toDomain() = WhatsAppSettings(
    businessId = businessId,
    establishmentId = establishmentId,
    establishmentName = establishmentName,
    whatsappPhone = whatsappPhone,
    phoneId = phoneId,
    whatsappBusinessId = whatsappBusinessId,
    token = token
)

fun WhatsAppSettings.toDto() = WhatsAppSettingsDto(
    businessId = businessId,
    establishmentId = establishmentId,
    establishmentName = establishmentName,
    whatsappPhone = whatsappPhone,
    phoneId = phoneId,
    whatsappBusinessId = whatsappBusinessId,
    token = token
)
