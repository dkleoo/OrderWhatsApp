package com.example.bakendorderwhatsapp.di

import com.example.bakendorderwhatsapp.data.dataBase.whatsappSettings.dao.WhatsAppSettingsDao
import com.example.bakendorderwhatsapp.data.repository.WhatsAppSettingsRepositoryImpl
import com.example.bakendorderwhatsapp.domain.repository.WhatsAppSettingsRepository
import com.example.bakendorderwhatsapp.domain.usecase.GetWhatsAppSettingsUseCase
import com.example.bakendorderwhatsapp.domain.usecase.SaveWhatsAppSettingsUseCase

class AppContainer {
    private val whatsAppSettingsDao = WhatsAppSettingsDao()

    private val whatsAppSettingsRepository: WhatsAppSettingsRepository =
        WhatsAppSettingsRepositoryImpl(whatsAppSettingsDao)

    val getWhatsAppSettingsUseCase = GetWhatsAppSettingsUseCase(whatsAppSettingsRepository)
    val saveWhatsAppSettingsUseCase = SaveWhatsAppSettingsUseCase(whatsAppSettingsRepository)
}
