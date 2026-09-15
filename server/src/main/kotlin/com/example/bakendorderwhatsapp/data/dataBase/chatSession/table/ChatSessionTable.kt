package com.example.bakendorderwhatsapp.data.dataBase.chatSession.table

import org.jetbrains.exposed.sql.Table

object ChatSessionTable : Table("chat_sessions") {
    val id = uuid("id").autoGenerate()
    val senderPhone = varchar("sender_phone", 32)
    val receiverPhone = varchar("receiver_phone", 32)
    val phoneId = varchar("phone_id", 128)
    val whatsappBusinessId = varchar("whatsapp_business_id", 128)
    val establishmentId = varchar("establishment_id", 128).default("")
    val establishmentName = varchar("establishment_name", 255).default("")
    val lastActivityAt = long("last_activity_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(senderPhone, phoneId)
    }
}
