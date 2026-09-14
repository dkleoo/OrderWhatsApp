package com.example.bakendorderwhatsapp.di

import com.example.bakendorderwhatsapp.data.client.WhatsAppGraphClient
import com.example.bakendorderwhatsapp.data.dataBase.whatsappSettings.dao.WhatsAppSettingsDao
import com.example.bakendorderwhatsapp.data.repository.WhatsAppSettingsRepositoryImpl
import com.example.bakendorderwhatsapp.domain.repository.WhatsAppSettingsRepository
import com.example.bakendorderwhatsapp.domain.usecase.GetWhatsAppSettingsUseCase
import com.example.bakendorderwhatsapp.domain.usecase.HandleWhatsAppWebhookUseCase
import com.example.bakendorderwhatsapp.domain.usecase.SaveWhatsAppSettingsUseCase
import com.example.bakendorderwhatsapp.domain.usecase.VerifyWhatsAppWebhookUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.json.Json

class AppContainer(
    config: ApplicationConfig
) {
    private val whatsAppSettingsDao = WhatsAppSettingsDao()

    private val whatsAppSettingsRepository: WhatsAppSettingsRepository =
        WhatsAppSettingsRepositoryImpl(whatsAppSettingsDao)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                    isLenient = true
                }
            )
        }
    }

    private val graphApiVersion = config.propertyOrNull("whatsapp.graphApiVersion")
        ?.getString()
        ?: "v25.0"

    private val verifyToken = System.getenv("WHATSAPP_VERIFY_TOKEN")
        ?: config.propertyOrNull("whatsapp.verifyToken")?.getString().orEmpty()

    private val autoReplyBody = System.getenv("WHATSAPP_AUTO_REPLY")
        ?: config.propertyOrNull("whatsapp.autoReply")?.getString()
        ?: "What can I help you with today?"

    private val messageSender = WhatsAppGraphClient(
        httpClient = httpClient,
        graphApiVersion = graphApiVersion
    )

    val getWhatsAppSettingsUseCase = GetWhatsAppSettingsUseCase(whatsAppSettingsRepository)
    val saveWhatsAppSettingsUseCase = SaveWhatsAppSettingsUseCase(whatsAppSettingsRepository)

    val verifyWhatsAppWebhookUseCase = VerifyWhatsAppWebhookUseCase(verifyToken)
    val handleWhatsAppWebhookUseCase = HandleWhatsAppWebhookUseCase(
        repository = whatsAppSettingsRepository,
        messageSender = messageSender,
        autoReplyBody = autoReplyBody
    )
}
