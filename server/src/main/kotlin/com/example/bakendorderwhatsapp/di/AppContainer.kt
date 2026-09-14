package com.example.bakendorderwhatsapp.di

import com.example.bakendorderwhatsapp.data.client.OllamaPosAssistantClient
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
import io.ktor.client.plugins.HttpTimeout
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
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }
    }

    private val graphApiVersion = config.propertyOrNull("whatsapp.graphApiVersion")
        ?.getString()
        ?: "v25.0"

    private val verifyToken = System.getenv("WHATSAPP_VERIFY_TOKEN")
        ?: config.propertyOrNull("whatsapp.verifyToken")?.getString().orEmpty()

    private val ollamaBaseUrl = System.getenv("OLLAMA_BASE_URL")
        ?: config.propertyOrNull("ollama.baseUrl")?.getString()
        ?: "http://127.0.0.1:11434"

    private val ollamaModel = System.getenv("OLLAMA_MODEL")
        ?: config.propertyOrNull("ollama.model")?.getString()
        ?: "qwen2.5:7b"

    private val posSystemPrompt = System.getenv("POS_SYSTEM_PROMPT")
        ?: config.propertyOrNull("ollama.systemPrompt")?.getString()
        ?: DEFAULT_POS_SYSTEM_PROMPT

    private val messageSender = WhatsAppGraphClient(
        httpClient = httpClient,
        graphApiVersion = graphApiVersion
    )

    private val posAssistantAi = OllamaPosAssistantClient(
        httpClient = httpClient,
        baseUrl = ollamaBaseUrl,
        model = ollamaModel,
        systemPrompt = posSystemPrompt,
        json = json
    )

    val getWhatsAppSettingsUseCase = GetWhatsAppSettingsUseCase(whatsAppSettingsRepository)
    val saveWhatsAppSettingsUseCase = SaveWhatsAppSettingsUseCase(whatsAppSettingsRepository)

    val verifyWhatsAppWebhookUseCase = VerifyWhatsAppWebhookUseCase(verifyToken)
    val handleWhatsAppWebhookUseCase = HandleWhatsAppWebhookUseCase(
        repository = whatsAppSettingsRepository,
        messageSender = messageSender,
        posAssistantAi = posAssistantAi
    )

    companion object {
        private val DEFAULT_POS_SYSTEM_PROMPT = """
Eres un asistente de sistema POS para una tienda.
Tu trabajo es ayudar al dueño o cajero a agregar productos al inventario por WhatsApp.

Cuando el usuario quiera agregar un producto, pide o confirma estos datos si faltan:
- nombre del producto
- precio
- cantidad / stock
- categoría (opcional)
- descripción corta (opcional)

Responde en español, claro y breve (máximo 2-3 párrafos cortos o una lista).
Si el mensaje trae todos los datos, resume el producto listo para registrar y confirma.
Si falta información, pregunta solo lo necesario.
No inventes precios ni stock. No hables de temas fuera del POS o la tienda.
        """.trimIndent()
    }
}
