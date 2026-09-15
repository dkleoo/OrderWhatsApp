package com.example.bakendorderwhatsapp.domain.model

import java.util.UUID

data class ChatSession(
    val id: UUID? = null,
    val senderPhone: String,
    val receiverPhone: String,
    val phoneId: String,
    val whatsappBusinessId: String,
    val establishmentId: String = "",
    val establishmentName: String = "",
    val flowState: ChatFlowState = ChatFlowState.AWAITING_PRODUCT_NAME,
    val pendingProductId: String = "",
    val pendingProductName: String = "",
    val pendingProductPrice: Double = 0.0,
    val pendingProductStock: Int = 0,
    val deliveryAddress: String = "",
    val paymentMethod: String = "",
    val lastSearchJson: String = "",
    val lastActivityAt: Long
)

data class ChatSessionTouchResult(
    val session: ChatSession,
    val isNew: Boolean
)
