package com.example.bakendorderwhatsapp.data.client

import com.example.bakendorderwhatsapp.domain.service.WhatsAppMessageSender
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

class WhatsAppGraphClient(
    private val httpClient: HttpClient,
    private val graphApiVersion: String = "v25.0"
) : WhatsAppMessageSender {

    private val log = LoggerFactory.getLogger(WhatsAppGraphClient::class.java)

    override suspend fun sendTextMessage(
        phoneNumberId: String,
        accessToken: String,
        to: String,
        body: String
    ) {
        val url = "https://graph.facebook.com/$graphApiVersion/$phoneNumberId/messages"
        val response = httpClient.post(url) {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                SendTextMessageRequest(
                    to = to,
                    text = TextBody(body = body)
                )
            )
        }

        val responseBody = response.bodyAsText()
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            log.error("Graph API error {}: {}", response.status, responseBody)
            error("Failed to send WhatsApp message: ${response.status} $responseBody")
        }

        log.info("WhatsApp message sent to {} via {}", to, phoneNumberId)
    }
}

@Serializable
private data class SendTextMessageRequest(
    @SerialName("messaging_product")
    val messagingProduct: String = "whatsapp",
    val to: String,
    val type: String = "text",
    val text: TextBody
)

@Serializable
private data class TextBody(
    val body: String
)
