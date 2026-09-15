package com.example.bakendorderwhatsapp.data.client

import com.example.bakendorderwhatsapp.domain.model.ProductSummary
import com.example.bakendorderwhatsapp.domain.service.WhatsAppMessageSender
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
        postMessage(
            phoneNumberId = phoneNumberId,
            accessToken = accessToken,
            payload = buildJsonObject {
                put("messaging_product", "whatsapp")
                put("to", to)
                put("type", "text")
                put("text", buildJsonObject { put("body", body) })
            }
        )
    }

    override suspend fun sendProductList(
        phoneNumberId: String,
        accessToken: String,
        to: String,
        bodyText: String,
        products: List<ProductSummary>
    ) {
        val rows = products.take(10).map { product ->
            buildJsonObject {
                put("id", product.id.take(200))
                put("title", product.name.take(24))
                put(
                    "description",
                    "Precio: ${formatMoney(product.price)} | Stock: ${product.stock}".take(72)
                )
            }
        }

        postMessage(
            phoneNumberId = phoneNumberId,
            accessToken = accessToken,
            payload = buildJsonObject {
                put("messaging_product", "whatsapp")
                put("to", to)
                put("type", "interactive")
                put(
                    "interactive",
                    buildJsonObject {
                        put("type", "list")
                        put("body", buildJsonObject { put("text", bodyText.take(1024)) })
                        put(
                            "action",
                            buildJsonObject {
                                put("button", "Ver productos")
                                put(
                                    "sections",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("title", "Resultados")
                                                put("rows", buildJsonArray {
                                                    rows.forEach { add(it) }
                                                })
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
    }

    override suspend fun sendYesNoButtons(
        phoneNumberId: String,
        accessToken: String,
        to: String,
        bodyText: String,
        yesId: String,
        noId: String,
        yesTitle: String,
        noTitle: String
    ) {
        postMessage(
            phoneNumberId = phoneNumberId,
            accessToken = accessToken,
            payload = buildJsonObject {
                put("messaging_product", "whatsapp")
                put("to", to)
                put("type", "interactive")
                put(
                    "interactive",
                    buildJsonObject {
                        put("type", "button")
                        put("body", buildJsonObject { put("text", bodyText.take(1024)) })
                        put(
                            "action",
                            buildJsonObject {
                                put(
                                    "buttons",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("type", "reply")
                                                put(
                                                    "reply",
                                                    buildJsonObject {
                                                        put("id", yesId)
                                                        put("title", yesTitle.take(20))
                                                    }
                                                )
                                            }
                                        )
                                        add(
                                            buildJsonObject {
                                                put("type", "reply")
                                                put(
                                                    "reply",
                                                    buildJsonObject {
                                                        put("id", noId)
                                                        put("title", noTitle.take(20))
                                                    }
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
    }

    private suspend fun postMessage(
        phoneNumberId: String,
        accessToken: String,
        payload: JsonObject
    ) {
        val url = "https://graph.facebook.com/$graphApiVersion/$phoneNumberId/messages"
        val response = httpClient.post(url) {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        val responseBody = response.bodyAsText()
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            log.error("Graph API error {}: {}", response.status, responseBody)
            error("Failed to send WhatsApp message: ${response.status} $responseBody")
        }

        log.info("WhatsApp message sent to phoneId={}", phoneNumberId)
    }

    private fun formatMoney(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)
}
