package com.example.bakendorderwhatsapp.data.dataBase.cartItem.table

import org.jetbrains.exposed.sql.Table

object CartItemTable : Table("cart_items") {
    val id = uuid("id").autoGenerate()
    val senderPhone = varchar("sender_phone", 32)
    val phoneId = varchar("phone_id", 128)
    val establishmentId = varchar("establishment_id", 128)
    val productId = varchar("product_id", 128)
    val productName = varchar("product_name", 255)
    val quantity = integer("quantity")
    val price = double("price")
    val deliveryAddress = text("delivery_address").default("")
    val paymentMethod = varchar("payment_method", 64).default("")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
