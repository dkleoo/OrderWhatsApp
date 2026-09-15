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
    val flowState = varchar("flow_state", 64).default("AWAITING_PRODUCT_NAME")
    val pendingProductId = varchar("pending_product_id", 128).default("")
    val pendingProductName = varchar("pending_product_name", 255).default("")
    val pendingProductPrice = double("pending_product_price").default(0.0)
    val pendingProductStock = integer("pending_product_stock").default(0)
    val customerName = varchar("customer_name", 255).default("")
    val deliveryAddress = text("delivery_address").default("")
    val paymentMethod = varchar("payment_method", 64).default("")
    val lastSearchJson = text("last_search_json").default("")
    val lastActivityAt = long("last_activity_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(senderPhone, phoneId)
    }
}
