package com.example.bakendorderwhatsapp.data.dataBase.order.table

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object OrderHeaderTable : Table("order_headers") {
    val id = uuid("id").autoGenerate()
    val senderPhone = varchar("sender_phone", 32)
    val phoneId = varchar("phone_id", 128)
    val establishmentId = varchar("establishment_id", 128)
    val customerName = varchar("customer_name", 255).default("")
    val deliveryAddress = text("delivery_address").default("")
    val paymentMethod = varchar("payment_method", 64).default("")
    val total = double("total").default(0.0)
    val status = varchar("status", 32).default("DRAFT")
    val createdAt = long("created_at")
    val completedAt = long("completed_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, senderPhone, phoneId, status)
    }
}

object OrderDetailTable : Table("order_details") {
    val id = uuid("id").autoGenerate()
    val orderHeaderId = uuid("order_header_id")
        .references(OrderHeaderTable.id, onDelete = ReferenceOption.CASCADE)
    val productId = varchar("product_id", 128)
    val productName = varchar("product_name", 255)
    val quantity = integer("quantity")
    val unitPrice = double("unit_price")
    val lineTotal = double("line_total")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, orderHeaderId, productId)
    }
}
