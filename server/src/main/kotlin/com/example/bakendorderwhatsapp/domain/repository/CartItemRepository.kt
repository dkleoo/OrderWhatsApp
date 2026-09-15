package com.example.bakendorderwhatsapp.domain.repository

import com.example.bakendorderwhatsapp.domain.model.CartItem

interface CartItemRepository {
    suspend fun add(item: CartItem): CartItem
    suspend fun listBySenderAndPhoneId(senderPhone: String, phoneId: String): List<CartItem>
    suspend fun updateCheckoutInfo(
        senderPhone: String,
        phoneId: String,
        deliveryAddress: String? = null,
        paymentMethod: String? = null
    ): Int
}
