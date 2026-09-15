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

    /**
     * @param force if true, always re-fetch from API. If false, skip when catalog already has rows.
     */
    suspend operator fun invoke(
        accessToken: String,
        establishmentId: String,
        force: Boolean = true
    ): Int {
        if (accessToken.isBlank()) {
            log.warn("Skip product sync: blank Bearer token (whatsapp_settings.token)")
            return 0
        }
        if (establishmentId.isBlank()) {
            log.warn("Skip product sync: blank establishmentId")
            return 0
        }

        if (!force) {
            val existing = productRepository.countByEstablishment(establishmentId)
            if (existing > 0) {
                log.info("Products already loaded ({}) for establishmentId={}", existing, establishmentId)
                return existing
            }
        }

        val products = withTimeoutOrNull(60_000) {
            productSync.fetchAllProducts(accessToken, establishmentId)
        } ?: run {
            log.warn("Product sync timed out for establishmentId={}", establishmentId)
            emptyList()
        }

        productRepository.replaceForEstablishment(establishmentId, products)
        log.info("Synced {} products into DB for establishmentId={}", products.size, establishmentId)
        return products.size
    }
}
