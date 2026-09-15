package com.example.bakendorderwhatsapp.domain.model

import java.util.UUID

data class OrderDetail(
    val id: UUID? = null,
    val orderHeaderId: UUID,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Double,
    val lineTotal: Double = unitPrice * quantity
)

data class OrderHeader(
    val id: UUID? = null,
    val senderPhone: String,
    val phoneId: String,
    val establishmentId: String,
    val customerName: String = "",
    val deliveryAddress: String = "",
    val paymentMethod: String = "",
    val total: Double = 0.0,
    val status: OrderStatus = OrderStatus.DRAFT,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val details: List<OrderDetail> = emptyList()
)
