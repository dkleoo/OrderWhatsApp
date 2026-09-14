package com.example.bakendorderwhatsapp.plugins

import com.example.bakendorderwhatsapp.presentation.dto.ErrorResponseDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto(cause.message ?: "Invalid request")
            )
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(cause.message ?: "Unexpected error")
            )
        }
    }
}
