package com.example.bakendorderwhatsapp.domain.service

interface PosAssistantAi {
    suspend fun reply(userMessage: String): String
}
