package com.example.bakendorderwhatsapp.domain.usecase

class VerifyWhatsAppWebhookUseCase(
    private val verifyToken: String
) {
    operator fun invoke(mode: String?, token: String?, challenge: String?): String {
        require(!verifyToken.isBlank()) {
            "WHATSAPP_VERIFY_TOKEN is not configured"
        }
        require(mode == "subscribe") { "Invalid hub.mode" }
        require(token == verifyToken) { "Invalid hub.verify_token" }
        require(!challenge.isNullOrBlank()) { "Missing hub.challenge" }
        return challenge
    }
}
