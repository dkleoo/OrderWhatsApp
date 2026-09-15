package com.example.bakendorderwhatsapp.data.repository

import com.example.bakendorderwhatsapp.data.dataBase.product.dao.ProductDao
import com.example.bakendorderwhatsapp.domain.model.ProductSummary
import com.example.bakendorderwhatsapp.domain.model.StoredProduct
import com.example.bakendorderwhatsapp.domain.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProductRepositoryImpl(
    private val dao: ProductDao
) : ProductRepository {

    override suspend fun replaceForEstablishment(
        establishmentId: String,
        products: List<StoredProduct>
    ) = withContext(Dispatchers.IO) {
        dao.replaceForEstablishment(establishmentId, products)
    }

    override suspend fun searchByName(
        establishmentId: String,
        name: String,
        limit: Int
    ): List<ProductSummary> = withContext(Dispatchers.IO) {
        dao.searchByName(establishmentId, name, limit)
    }

    override suspend fun countByEstablishment(establishmentId: String): Int =
        withContext(Dispatchers.IO) {
            dao.countByEstablishment(establishmentId)
        }
}
