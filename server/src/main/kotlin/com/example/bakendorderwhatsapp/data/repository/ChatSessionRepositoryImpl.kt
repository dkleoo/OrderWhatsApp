package com.example.bakendorderwhatsapp.data.repository

import com.example.bakendorderwhatsapp.data.dataBase.chatSession.dao.ChatSessionDao
import com.example.bakendorderwhatsapp.domain.model.ChatSession
import com.example.bakendorderwhatsapp.domain.model.ChatSessionTouchResult
import com.example.bakendorderwhatsapp.domain.repository.ChatSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatSessionRepositoryImpl(
    private val dao: ChatSessionDao
) : ChatSessionRepository {

    override suspend fun findBySenderAndPhoneId(senderPhone: String, phoneId: String): ChatSession? =
        withContext(Dispatchers.IO) {
            dao.findBySenderAndPhoneId(senderPhone, phoneId)
        }

    override suspend fun create(session: ChatSession): ChatSession =
        withContext(Dispatchers.IO) {
            dao.create(session)
        }

    override suspend fun refreshActivity(
        senderPhone: String,
        phoneId: String,
        receiverPhone: String,
        whatsappBusinessId: String,
        lastActivityAt: Long
    ): ChatSession? = withContext(Dispatchers.IO) {
        dao.refreshActivity(
            senderPhone = senderPhone,
            phoneId = phoneId,
            receiverPhone = receiverPhone,
            whatsappBusinessId = whatsappBusinessId,
            lastActivityAt = lastActivityAt
        )
    }

    override suspend fun touchOrCreate(session: ChatSession): ChatSessionTouchResult =
        withContext(Dispatchers.IO) {
            dao.touchOrCreate(session)
        }

    override suspend fun deleteInactive(olderThanEpochMs: Long): Int =
        withContext(Dispatchers.IO) {
            dao.deleteInactiveBefore(olderThanEpochMs)
        }
}
