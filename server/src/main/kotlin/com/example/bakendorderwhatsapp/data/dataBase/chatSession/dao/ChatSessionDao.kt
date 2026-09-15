package com.example.bakendorderwhatsapp.data.dataBase.chatSession.dao

import com.example.bakendorderwhatsapp.data.dataBase.chatSession.table.ChatSessionTable
import com.example.bakendorderwhatsapp.domain.model.ChatFlowState
import com.example.bakendorderwhatsapp.domain.model.ChatSession
import com.example.bakendorderwhatsapp.domain.model.ChatSessionTouchResult
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

class ChatSessionDao {

    fun findBySenderAndPhoneId(senderPhone: String, phoneId: String): ChatSession? = transaction {
        selectBySenderAndPhoneId(senderPhone, phoneId)?.toDomain()
    }

    fun create(session: ChatSession): ChatSession = transaction {
        insertSession(session)
    }

    fun refreshActivity(
        senderPhone: String,
        phoneId: String,
        receiverPhone: String,
        whatsappBusinessId: String,
        lastActivityAt: Long
    ): ChatSession? = transaction {
        val existing = selectBySenderAndPhoneId(senderPhone, phoneId) ?: return@transaction null
        val id = existing[ChatSessionTable.id]
        ChatSessionTable.update({ ChatSessionTable.id eq id }) {
            it[ChatSessionTable.receiverPhone] = receiverPhone
            it[ChatSessionTable.whatsappBusinessId] = whatsappBusinessId
            it[ChatSessionTable.lastActivityAt] = lastActivityAt
        }
        selectById(id)?.toDomain()
    }

    fun update(session: ChatSession): ChatSession = transaction {
        val id = session.id ?: error("ChatSession id is required for update")
        ChatSessionTable.update({ ChatSessionTable.id eq id }) {
            it[receiverPhone] = session.receiverPhone
            it[whatsappBusinessId] = session.whatsappBusinessId
            it[establishmentId] = session.establishmentId
            it[establishmentName] = session.establishmentName
            it[flowState] = session.flowState.name
            it[pendingProductId] = session.pendingProductId
            it[pendingProductName] = session.pendingProductName
            it[pendingProductPrice] = session.pendingProductPrice
            it[pendingProductStock] = session.pendingProductStock
            it[customerName] = session.customerName
            it[deliveryAddress] = session.deliveryAddress
            it[paymentMethod] = session.paymentMethod
            it[lastSearchJson] = session.lastSearchJson
            it[lastActivityAt] = session.lastActivityAt
        }
        selectById(id)!!.toDomain()
    }

    fun touchOrCreate(session: ChatSession): ChatSessionTouchResult = transaction {
        val existing = selectBySenderAndPhoneId(session.senderPhone, session.phoneId)
        if (existing != null) {
            val id = existing[ChatSessionTable.id]
            ChatSessionTable.update({ ChatSessionTable.id eq id }) {
                it[receiverPhone] = session.receiverPhone
                it[whatsappBusinessId] = session.whatsappBusinessId
                it[lastActivityAt] = session.lastActivityAt
            }
            ChatSessionTouchResult(session = selectById(id)!!.toDomain(), isNew = false)
        } else {
            ChatSessionTouchResult(session = insertSession(session), isNew = true)
        }
    }

    fun findInactiveBefore(cutoffEpochMs: Long): List<ChatSession> = transaction {
        ChatSessionTable
            .selectAll()
            .where { ChatSessionTable.lastActivityAt less cutoffEpochMs }
            .map { it.toDomain() }
    }

    fun deleteInactiveBefore(cutoffEpochMs: Long): Int = transaction {
        ChatSessionTable.deleteWhere {
            ChatSessionTable.lastActivityAt less cutoffEpochMs
        }
    }

    private fun insertSession(session: ChatSession): ChatSession {
        val insertedId = ChatSessionTable.insert {
            it[id] = session.id ?: UUID.randomUUID()
            it[senderPhone] = session.senderPhone
            it[receiverPhone] = session.receiverPhone
            it[phoneId] = session.phoneId
            it[whatsappBusinessId] = session.whatsappBusinessId
            it[establishmentId] = session.establishmentId
            it[establishmentName] = session.establishmentName
            it[flowState] = session.flowState.name
            it[pendingProductId] = session.pendingProductId
            it[pendingProductName] = session.pendingProductName
            it[pendingProductPrice] = session.pendingProductPrice
            it[pendingProductStock] = session.pendingProductStock
            it[customerName] = session.customerName
            it[deliveryAddress] = session.deliveryAddress
            it[paymentMethod] = session.paymentMethod
            it[lastSearchJson] = session.lastSearchJson
            it[lastActivityAt] = session.lastActivityAt
        } get ChatSessionTable.id
        return selectById(insertedId)!!.toDomain()
    }

    private fun selectBySenderAndPhoneId(senderPhone: String, phoneId: String): ResultRow? =
        ChatSessionTable
            .selectAll()
            .where {
                (ChatSessionTable.senderPhone eq senderPhone) and
                    (ChatSessionTable.phoneId eq phoneId)
            }
            .singleOrNull()

    private fun selectById(id: UUID): ResultRow? =
        ChatSessionTable
            .selectAll()
            .where { ChatSessionTable.id eq id }
            .singleOrNull()

    private fun ResultRow.toDomain() = ChatSession(
        id = this[ChatSessionTable.id],
        senderPhone = this[ChatSessionTable.senderPhone],
        receiverPhone = this[ChatSessionTable.receiverPhone],
        phoneId = this[ChatSessionTable.phoneId],
        whatsappBusinessId = this[ChatSessionTable.whatsappBusinessId],
        establishmentId = this[ChatSessionTable.establishmentId],
        establishmentName = this[ChatSessionTable.establishmentName],
        flowState = runCatching {
            ChatFlowState.valueOf(this[ChatSessionTable.flowState])
        }.getOrDefault(ChatFlowState.AWAITING_PRODUCT_NAME),
        pendingProductId = this[ChatSessionTable.pendingProductId],
        pendingProductName = this[ChatSessionTable.pendingProductName],
        pendingProductPrice = this[ChatSessionTable.pendingProductPrice],
        pendingProductStock = this[ChatSessionTable.pendingProductStock],
        customerName = this[ChatSessionTable.customerName],
        deliveryAddress = this[ChatSessionTable.deliveryAddress],
        paymentMethod = this[ChatSessionTable.paymentMethod],
        lastSearchJson = this[ChatSessionTable.lastSearchJson],
        lastActivityAt = this[ChatSessionTable.lastActivityAt]
    )
}
