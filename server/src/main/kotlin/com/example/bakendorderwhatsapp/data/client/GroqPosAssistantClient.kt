package com.example.bakendorderwhatsapp.data.client

import com.example.bakendorderwhatsapp.domain.service.PosAssistantAi
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class GroqPosAssistantClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val model: String,
    private val systemPrompt: String,
    private val baseUrl: String = "https://api.groq.com/openai/v1",
    private val json: Json = Json { ignoreUnknownKeys = true }
) : PosAssistantAi {

    private val log = LoggerFactory.getLogger(GroqPosAssistantClient::class.java)

    override suspend fun reply(userMessage: String): String {
        require(apiKey.isNotBlank()) {
            "GROQ_API_KEY is missing. Set it in Render → Environment."
        }

        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        val response = httpClient.post(url) {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(
                GroqChatRequest(
                    model = model,
                    messages = listOf(
                        GroqMessage(role = "system", content = systemPrompt),
                        GroqMessage(role = "user", content = userMessage)
                    )
                )
            )
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            log.error("Groq error {}: {}", response.status, body)
            error("Groq request failed: ${response.status} $body")
        }

        val parsed = json.decodeFromString<GroqChatResponse>(body)
        val content = parsed.choices
            .firstOrNull()
            ?.message
            ?.content
            ?.trim()
            .orEmpty()

        require(content.isNotBlank()) { "Groq returned an empty response" }
        return content.take(3500)
    }
}

@Serializable
private data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>
)

@Serializable
private data class GroqMessage(
    val role: String,
    val content: String
)

@Serializable
private data class GroqChatResponse(
    val choices: List<GroqChoice> = emptyList()
)

@Serializable
private data class GroqChoice(
    val message: GroqMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)
