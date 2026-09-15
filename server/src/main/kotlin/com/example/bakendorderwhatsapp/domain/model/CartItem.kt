package com.example.bakendorderwhatsapp.domain.model

import java.util.UUID

data class CartItem(
    val id: UUID? = null,
    val senderPhone: String,
    val phoneId: String,
    val establishmentId: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val price: Double,
    val deliveryAddress: String = "",
    val paymentMethod: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
