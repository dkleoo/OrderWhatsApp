package com.example.bakendorderwhatsapp.domain.repository

import com.example.bakendorderwhatsapp.domain.model.WhatsAppSettings

interface WhatsAppSettingsRepository {
    suspend fun getByBusinessAndEstablishment(
        businessId: String,
        establishmentId: String
    ): WhatsAppSettings?

    suspend fun getByBusinessId(businessId: String): List<WhatsAppSettings>

    suspend fun getByPhoneId(phoneId: String): WhatsAppSettings?

    suspend fun save(settings: WhatsAppSettings): WhatsAppSettings
}
