package com.example.bakendorderwhatsapp.domain.repository

import com.example.bakendorderwhatsapp.domain.model.OrderHeader

interface OrderRepository {
    suspend fun addOrIncrementDetail(
        senderPhone: String,
        phoneId: String,
        establishmentId: String,
        productId: String,
        productName: String,
        quantity: Int,
        unitPrice: Double
    ): OrderHeader

    suspend fun findDraft(senderPhone: String, phoneId: String): OrderHeader?

    suspend fun updateDraftCheckout(
        senderPhone: String,
        phoneId: String,
        customerName: String? = null,
        deliveryAddress: String? = null,
        paymentMethod: String? = null
    ): OrderHeader?

    suspend fun completeDraft(
        senderPhone: String,
        phoneId: String,
        customerName: String,
        deliveryAddress: String,
        paymentMethod: String
    ): OrderHeader?

    /** Only incomplete (DRAFT) orders. Completed orders are never deleted. */
    suspend fun deleteDraft(senderPhone: String, phoneId: String): Int
}
