package com.example.bakendorderwhatsapp.domain.repository

import com.example.bakendorderwhatsapp.domain.model.ProductSummary
import com.example.bakendorderwhatsapp.domain.model.StoredProduct

interface ProductRepository {
    suspend fun replaceForEstablishment(establishmentId: String, products: List<StoredProduct>)
    suspend fun searchByName(establishmentId: String, name: String, limit: Int = 10): List<ProductSummary>
    suspend fun countByEstablishment(establishmentId: String): Int
}
