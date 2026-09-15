package com.example.bakendorderwhatsapp.domain.usecase

import com.example.bakendorderwhatsapp.domain.repository.ProductRepository
import com.example.bakendorderwhatsapp.domain.service.EstablishmentProductSync
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

class SyncEstablishmentProductsUseCase(
    private val productSync: EstablishmentProductSync,
    private val productRepository: ProductRepository
) {
    private val log = LoggerFactory.getLogger(SyncEstablishmentProductsUseCase::class.java)

    suspend operator fun invoke(accessToken: String, establishmentId: String): Int {
        if (accessToken.isBlank() || establishmentId.isBlank()) {
            log.warn("Skip product sync: missing token or establishmentId")
            return 0
        }

        val products = withTimeoutOrNull(45_000) {
            productSync.fetchAllProducts(accessToken, establishmentId)
        } ?: run {
            log.warn("Product sync timed out for establishmentId={}", establishmentId)
            emptyList()
        }

        productRepository.replaceForEstablishment(establishmentId, products)
        log.info("Synced {} products for establishmentId={}", products.size, establishmentId)
        return products.size
    }
}
