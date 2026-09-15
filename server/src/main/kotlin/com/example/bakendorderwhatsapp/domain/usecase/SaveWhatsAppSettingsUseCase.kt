package com.example.bakendorderwhatsapp.domain.usecase

import com.example.bakendorderwhatsapp.domain.model.WhatsAppSettings
import com.example.bakendorderwhatsapp.domain.repository.WhatsAppSettingsRepository

class SaveWhatsAppSettingsUseCase(
    private val repository: WhatsAppSettingsRepository,
    private val sharedWhatsAppToken: String
) {
    suspend operator fun invoke(settings: WhatsAppSettings): WhatsAppSettings {
        require(settings.businessId.isNotBlank()) { "businessId is required" }
        require(settings.establishmentId.isNotBlank()) { "establishmentId is required" }
        require(settings.whatsappPhone.isNotBlank()) { "whatsappPhone is required" }
        require(settings.phoneId.isNotBlank()) { "phoneId is required" }
        require(sharedWhatsAppToken.isNotBlank()) {
            "WHATSAPP_ACCESS_TOKEN is missing. Set it in Render → Environment."
        }

        // Mismo token global (env) para todos los registros.
        return repository.save(
            settings.copy(
                whatsappToken = sharedWhatsAppToken,
                token = settings.token.ifBlank { sharedWhatsAppToken }
            )
        )
    }
}
