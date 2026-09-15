package com.example.bakendorderwhatsapp.domain.service

import com.example.bakendorderwhatsapp.domain.model.ProductSummary

interface ProductCatalog {
    suspend fun searchByName(
        establishmentId: String,
        name: String,
        pageNumber: Int = 1,
        pageSize: Int = 10
    ): List<ProductSummary>
}
