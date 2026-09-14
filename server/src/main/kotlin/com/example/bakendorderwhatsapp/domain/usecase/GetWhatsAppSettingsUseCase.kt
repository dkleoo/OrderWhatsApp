package com.example.bakendorderwhatsapp.domain.usecase

import com.example.bakendorderwhatsapp.domain.model.WhatsAppSettings
import com.example.bakendorderwhatsapp.domain.repository.WhatsAppSettingsRepository

class GetWhatsAppSettingsUseCase(
    private val repository: WhatsAppSettingsRepository
) {
    suspend operator fun invoke(
        businessId: String,
        establishmentId: String? = null
    ): List<WhatsAppSettings> {
        require(businessId.isNotBlank()) { "businessId is required" }

        return if (!establishmentId.isNullOrBlank()) {
            listOfNotNull(repository.getByBusinessAndEstablishment(businessId, establishmentId))
        } else {
            repository.getByBusinessId(businessId)
        }
    }
}
