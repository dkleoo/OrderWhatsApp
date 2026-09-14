package com.example.bakendorderwhatsapp.plugins

import com.example.bakendorderwhatsapp.di.AppContainer
import com.example.bakendorderwhatsapp.presentation.routes.whatsAppSettingsRoutes
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting(container: AppContainer) {
    routing {
        get("/") {
            call.respondText("bakendOrderWhatsApp API is running")
        }

        whatsAppSettingsRoutes(
            getWhatsAppSettings = container.getWhatsAppSettingsUseCase,
            saveWhatsAppSettings = container.saveWhatsAppSettingsUseCase
        )
    }
}
