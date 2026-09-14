package com.example.bakendorderwhatsapp.presentation.routes

import com.example.bakendorderwhatsapp.domain.usecase.GetWhatsAppSettingsUseCase
import com.example.bakendorderwhatsapp.domain.usecase.SaveWhatsAppSettingsUseCase
import com.example.bakendorderwhatsapp.presentation.dto.ErrorResponseDto
import com.example.bakendorderwhatsapp.presentation.dto.WhatsAppSettingsDto
import com.example.bakendorderwhatsapp.presentation.dto.toDomain
import com.example.bakendorderwhatsapp.presentation.dto.toDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.whatsAppSettingsRoutes(
    getWhatsAppSettings: GetWhatsAppSettingsUseCase,
    saveWhatsAppSettings: SaveWhatsAppSettingsUseCase
) {
    route("/api/whatsapp-settings") {
        get {
            val businessId = call.request.queryParameters["businessId"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponseDto("Query param businessId is required")
                )
            val establishmentId = call.request.queryParameters["establishmentId"]

            val result = getWhatsAppSettings(businessId, establishmentId)
            when {
                result.isEmpty() -> call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponseDto("WhatsApp settings not found")
                )
                !establishmentId.isNullOrBlank() -> call.respond(
                    HttpStatusCode.OK,
                    result.first().toDto()
                )
                else -> call.respond(HttpStatusCode.OK, result.map { it.toDto() })
            }
        }

        post {
            val body = call.receive<WhatsAppSettingsDto>()
            val saved = saveWhatsAppSettings(body.toDomain())
            call.respond(HttpStatusCode.Created, saved.toDto())
        }
    }
}
