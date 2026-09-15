package com.example.bakendorderwhatsapp.domain.model

enum class ChatFlowState {
    AWAITING_PRODUCT_NAME,
    AWAITING_PRODUCT_SELECTION,
    AWAITING_ADD_CONFIRMATION,
    AWAITING_MORE_PRODUCTS,
    AWAITING_ADDRESS,
    AWAITING_PAYMENT,
    COMPLETED
}
