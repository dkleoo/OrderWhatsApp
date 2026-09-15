package com.example.bakendorderwhatsapp.domain.service

import com.example.bakendorderwhatsapp.domain.model.ProductSummary

interface WhatsAppMessageSender {
    suspend fun sendTextMessage(
        phoneNumberId: String,
        accessToken: String,
        to: String,
        body: String
    )

    suspend fun sendProductList(
        phoneNumberId: String,
        accessToken: String,
        to: String,
        bodyText: String,
        products: List<ProductSummary>
    )

    suspend fun sendYesNoButtons(
        phoneNumberId: String,
        accessToken: String,
        to: String,
        bodyText: String,
        yesId: String = "confirm_yes",
        noId: String = "confirm_no",
        yesTitle: String = "Sí",
        noTitle: String = "No"
    )
}
