package com.example.bakendorderwhatsapp.domain.service

import com.example.bakendorderwhatsapp.domain.model.StoredProduct

interface EstablishmentProductSync {
    suspend fun fetchAllProducts(
        accessToken: String,
        establishmentId: String
    ): List<StoredProduct>
}
