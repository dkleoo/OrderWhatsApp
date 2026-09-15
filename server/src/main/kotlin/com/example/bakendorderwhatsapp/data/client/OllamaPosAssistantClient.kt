package com.example.bakendorderwhatsapp.data.client

import com.example.bakendorderwhatsapp.domain.service.PosAssistantAi
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class OllamaPosAssistantClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val model: String,
    private val systemPrompt: String,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : PosAssistantAi {

    private val log = LoggerFactory.getLogger(OllamaPosAssistantClient::class.java)

    override suspend fun reply(
        userMessage: String,
        storeName: String,
        isNewSession: Boolean
    ): String {
        val storeLabel = storeName.ifBlank { "la tienda" }
        val dynamicSystem = buildString {
            appendLine(systemPrompt)
            appendLine()
            appendLine("Nombre de la tienda: $storeLabel")
            if (isNewSession) {
                appendLine(
                    "Esta es una sesión nueva. Tu primera respuesta DEBE empezar con: " +
                        "\"Bienvenido/a a $storeLabel\" y luego continuar con agregar productos."
                )
            }
        }

        val url = "${baseUrl.trimEnd('/')}/api/chat"
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(
                OllamaChatRequest(
                    model = model,
                    stream = false,
                    messages = listOf(
                        OllamaMessage(role = "system", content = dynamicSystem),
                        OllamaMessage(role = "user", content = userMessage)
                    )
                )
            )
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            log.error("Ollama error {}: {}", response.status, body)
            error("Ollama request failed: ${response.status}")
        }

        val parsed = json.decodeFromString<OllamaChatResponse>(body)
        val content = parsed.message?.content?.trim().orEmpty()
        require(content.isNotBlank()) { "Ollama returned an empty response" }
        return content.take(3500)
    }
}

@Serializable
private data class OllamaChatRequest(
    val model: String,
    val stream: Boolean = false,
    val messages: List<OllamaMessage>
)

@Serializable
private data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
private data class OllamaChatResponse(
    val message: OllamaMessage? = null
)
