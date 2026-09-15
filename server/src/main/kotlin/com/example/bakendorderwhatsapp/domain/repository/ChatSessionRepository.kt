package com.example.bakendorderwhatsapp.domain.repository

import com.example.bakendorderwhatsapp.domain.model.ChatSession
import com.example.bakendorderwhatsapp.domain.model.ChatSessionTouchResult

interface ChatSessionRepository {
    suspend fun findBySenderAndPhoneId(senderPhone: String, phoneId: String): ChatSession?
    suspend fun create(session: ChatSession): ChatSession
    suspend fun update(session: ChatSession): ChatSession
    suspend fun refreshActivity(
        senderPhone: String,
        phoneId: String,
        receiverPhone: String,
        whatsappBusinessId: String,
        lastActivityAt: Long
    ): ChatSession?

    suspend fun touchOrCreate(session: ChatSession): ChatSessionTouchResult
    suspend fun deleteInactive(olderThanEpochMs: Long): Int
}
