package com.example.bakendorderwhatsapp.di

import com.example.bakendorderwhatsapp.config.ApiEnvironments
import com.example.bakendorderwhatsapp.data.client.EstablishmentApiClient
import com.example.bakendorderwhatsapp.data.client.ProductApiClient
import com.example.bakendorderwhatsapp.data.client.WhatsAppGraphClient
import com.example.bakendorderwhatsapp.data.dataBase.cartItem.dao.CartItemDao
import com.example.bakendorderwhatsapp.data.dataBase.chatSession.dao.ChatSessionDao
import com.example.bakendorderwhatsapp.data.dataBase.whatsappSettings.dao.WhatsAppSettingsDao
import com.example.bakendorderwhatsapp.data.repository.CartItemRepositoryImpl
import com.example.bakendorderwhatsapp.data.repository.ChatSessionRepositoryImpl
import com.example.bakendorderwhatsapp.data.repository.WhatsAppSettingsRepositoryImpl
import com.example.bakendorderwhatsapp.domain.repository.CartItemRepository
import com.example.bakendorderwhatsapp.domain.repository.ChatSessionRepository
import com.example.bakendorderwhatsapp.domain.repository.WhatsAppSettingsRepository
import com.example.bakendorderwhatsapp.domain.usecase.CleanupInactiveChatSessionsUseCase
import com.example.bakendorderwhatsapp.domain.usecase.GetWhatsAppSettingsUseCase
import com.example.bakendorderwhatsapp.domain.usecase.HandleWhatsAppWebhookUseCase
import com.example.bakendorderwhatsapp.domain.usecase.SaveWhatsAppSettingsUseCase
import com.example.bakendorderwhatsapp.domain.usecase.TouchChatSessionUseCase
import com.example.bakendorderwhatsapp.domain.usecase.VerifyWhatsAppWebhookUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.json.Json

class AppContainer(
    config: ApplicationConfig
) {
    private val whatsAppSettingsDao = WhatsAppSettingsDao()
    private val chatSessionDao = ChatSessionDao()
    private val cartItemDao = CartItemDao()

    private val whatsAppSettingsRepository: WhatsAppSettingsRepository =
        WhatsAppSettingsRepositoryImpl(whatsAppSettingsDao)

    private val chatSessionRepository: ChatSessionRepository =
        ChatSessionRepositoryImpl(chatSessionDao)

    private val cartItemRepository: CartItemRepository =
        CartItemRepositoryImpl(cartItemDao)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }

    private val apiEnvironment = ApiEnvironments.resolve(
        System.getenv("API_ENV")
            ?: config.propertyOrNull("api.env")?.getString()
    )

    private val graphApiVersion = config.propertyOrNull("whatsapp.graphApiVersion")
        ?.getString()
        ?: "v25.0"

    private val verifyToken = System.getenv("WHATSAPP_VERIFY_TOKEN")
        ?: config.propertyOrNull("whatsapp.verifyToken")?.getString().orEmpty()

    private val whatsappAccessToken = System.getenv("WHATSAPP_ACCESS_TOKEN")
        ?: config.propertyOrNull("whatsapp.accessToken")?.getString().orEmpty()

    private val messageSender = WhatsAppGraphClient(
        httpClient = httpClient,
        graphApiVersion = graphApiVersion
    )

    private val establishmentCatalog = EstablishmentApiClient(
        httpClient = httpClient,
        apiConfig = apiEnvironment,
        json = json
    )

    private val productCatalog = ProductApiClient(
        httpClient = httpClient,
        apiConfig = apiEnvironment,
        json = json
    )

    val getWhatsAppSettingsUseCase = GetWhatsAppSettingsUseCase(whatsAppSettingsRepository)
    val saveWhatsAppSettingsUseCase = SaveWhatsAppSettingsUseCase(whatsAppSettingsRepository)

    val touchChatSessionUseCase = TouchChatSessionUseCase(
        repository = chatSessionRepository,
        establishmentCatalog = establishmentCatalog
    )
    val cleanupInactiveChatSessionsUseCase = CleanupInactiveChatSessionsUseCase(chatSessionRepository)

    val verifyWhatsAppWebhookUseCase = VerifyWhatsAppWebhookUseCase(verifyToken)
    val handleWhatsAppWebhookUseCase = HandleWhatsAppWebhookUseCase(
        settingsRepository = whatsAppSettingsRepository,
        chatSessionRepository = chatSessionRepository,
        cartItemRepository = cartItemRepository,
        productCatalog = productCatalog,
        messageSender = messageSender,
        touchChatSession = touchChatSessionUseCase,
        whatsappAccessToken = whatsappAccessToken,
        json = json
    )
}
