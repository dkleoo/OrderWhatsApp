package com.example.bakendorderwhatsapp.data.repository

import com.example.bakendorderwhatsapp.data.dataBase.whatsappSettings.dao.WhatsAppSettingsDao
import com.example.bakendorderwhatsapp.domain.model.WhatsAppSettings
import com.example.bakendorderwhatsapp.domain.repository.WhatsAppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhatsAppSettingsRepositoryImpl(
    private val dao: WhatsAppSettingsDao
) : WhatsAppSettingsRepository {

    override suspend fun getByBusinessAndEstablishment(
        businessId: String,
        establishmentId: String
    ): WhatsAppSettings? = withContext(Dispatchers.IO) {
        dao.findByBusinessAndEstablishment(businessId, establishmentId)
    }

    override suspend fun getByBusinessId(businessId: String): List<WhatsAppSettings> =
        withContext(Dispatchers.IO) {
            dao.findByBusinessId(businessId)
        }

    override suspend fun getByPhoneId(phoneId: String): WhatsAppSettings? =
        withContext(Dispatchers.IO) {
            dao.findByPhoneId(phoneId)
        }

    override suspend fun save(settings: WhatsAppSettings): WhatsAppSettings =
        withContext(Dispatchers.IO) {
            dao.upsert(settings)
        }
}
