package com.example.bakendorderwhatsapp.di

import com.example.bakendorderwhatsapp.config.ApiEnvironments
import com.example.bakendorderwhatsapp.data.client.EstablishmentApiClient
import com.example.bakendorderwhatsapp.data.client.GroqPosAssistantClient
import com.example.bakendorderwhatsapp.data.client.WhatsAppGraphClient
import com.example.bakendorderwhatsapp.data.dataBase.chatSession.dao.ChatSessionDao
import com.example.bakendorderwhatsapp.data.dataBase.whatsappSettings.dao.WhatsAppSettingsDao
import com.example.bakendorderwhatsapp.data.repository.ChatSessionRepositoryImpl
import com.example.bakendorderwhatsapp.data.repository.WhatsAppSettingsRepositoryImpl
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

    private val whatsAppSettingsRepository: WhatsAppSettingsRepository =
        WhatsAppSettingsRepositoryImpl(whatsAppSettingsDao)

    private val chatSessionRepository: ChatSessionRepository =
        ChatSessionRepositoryImpl(chatSessionDao)

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

    private val sharedWhatsAppToken = System.getenv("WHATSAPP_ACCESS_TOKEN")
        ?: config.propertyOrNull("whatsapp.accessToken")?.getString().orEmpty()

    private val groqApiKey = System.getenv("GROQ_API_KEY")
        ?: config.propertyOrNull("groq.apiKey")?.getString().orEmpty()

    private val groqModel = System.getenv("GROQ_MODEL")
        ?: config.propertyOrNull("groq.model")?.getString()
        ?: "openai/gpt-oss-120b"

    private val posSystemPrompt = System.getenv("POS_SYSTEM_PROMPT")
        ?: config.propertyOrNull("groq.systemPrompt")?.getString()
        ?: DEFAULT_POS_SYSTEM_PROMPT

    private val messageSender = WhatsAppGraphClient(
        httpClient = httpClient,
        graphApiVersion = graphApiVersion
    )

    private val establishmentCatalog = EstablishmentApiClient(
        httpClient = httpClient,
        apiConfig = apiEnvironment,
        json = json
    )

    private val posAssistantAi = GroqPosAssistantClient(
        httpClient = httpClient,
        apiKey = groqApiKey,
        model = groqModel,
        systemPrompt = posSystemPrompt,
        json = json
    )

    val getWhatsAppSettingsUseCase = GetWhatsAppSettingsUseCase(whatsAppSettingsRepository)
    val saveWhatsAppSettingsUseCase = SaveWhatsAppSettingsUseCase(
        repository = whatsAppSettingsRepository,
        sharedWhatsAppToken = sharedWhatsAppToken
    )

    val touchChatSessionUseCase = TouchChatSessionUseCase(
        repository = chatSessionRepository,
        establishmentCatalog = establishmentCatalog
    )
    val cleanupInactiveChatSessionsUseCase = CleanupInactiveChatSessionsUseCase(chatSessionRepository)

    val verifyWhatsAppWebhookUseCase = VerifyWhatsAppWebhookUseCase(verifyToken)
    val handleWhatsAppWebhookUseCase = HandleWhatsAppWebhookUseCase(
        repository = whatsAppSettingsRepository,
        messageSender = messageSender,
        posAssistantAi = posAssistantAi,
        touchChatSession = touchChatSessionUseCase,
        sharedWhatsAppToken = sharedWhatsAppToken
    )

    companion object {
        private val DEFAULT_POS_SYSTEM_PROMPT = """
Eres un asistente de sistema POS para una tienda por WhatsApp.
Tu ÚNICO objetivo es ayudar a AGREGAR PRODUCTOS. No te desvíes a otros temas.

Reglas estrictas:
1) Solo solicita el NOMBRE del producto. No pidas precio, stock, categoría ni descripción al usuario.
2) Cuando el usuario diga un nombre de producto, responde con:
   - el precio del producto
   - el stock disponible en ese momento
   - confirmación de que quedó agregado al carrito
3) En cada respuesta, anuncia claramente qué productos tiene actualmente en el carrito.
4) Si el usuario habla de algo que no sea agregar productos, redirígelo amablemente al objetivo (agregar productos por nombre).
5) Responde siempre en español, breve y claro.
6) No inventes datos si no los conoces; indica que aún no tienes precio/stock y pide solo el nombre del siguiente producto.
        """.trimIndent()
    }
}
