package com.example.bakendorderwhatsapp.presentation.routes

import com.example.bakendorderwhatsapp.domain.usecase.HandleWhatsAppWebhookUseCase
import com.example.bakendorderwhatsapp.domain.usecase.VerifyWhatsAppWebhookUseCase
import com.example.bakendorderwhatsapp.presentation.dto.WhatsAppWebhookPayloadDto
import com.example.bakendorderwhatsapp.presentation.dto.toIncomingMessages
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun Route.whatsAppWebhookRoutes(
    verifyWebhook: VerifyWhatsAppWebhookUseCase,
    handleWebhook: HandleWhatsAppWebhookUseCase
) {
    route("/webhook") {
        get {
            val mode = call.request.queryParameters["hub.mode"]
            val token = call.request.queryParameters["hub.verify_token"]
            val challenge = call.request.queryParameters["hub.challenge"]

            try {
                val result = verifyWebhook(mode, token, challenge)
                call.respondText(result)
            } catch (e: IllegalArgumentException) {
                call.application.log.warn("Webhook verification failed: {}", e.message)
                call.respond(HttpStatusCode.Forbidden, "Verification failed")
            }
        }

        post {
            val payload = call.receive<WhatsAppWebhookPayloadDto>()
            val messages = payload.toIncomingMessages()
            val appLog = call.application.log

            // Meta requires a fast 200 OK; process replies asynchronously.
            call.respond(HttpStatusCode.OK)

            if (messages.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        handleWebhook(messages)
                    }.onFailure {
                        appLog.error("Failed handling webhook messages", it)
                    }
                }
            }
        }
    }
}
