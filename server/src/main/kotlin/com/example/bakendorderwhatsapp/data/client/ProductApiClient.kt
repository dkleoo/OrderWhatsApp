package com.example.bakendorderwhatsapp.data.client

import com.example.bakendorderwhatsapp.config.ApiEnvironmentConfig
import com.example.bakendorderwhatsapp.domain.model.ProductSummary
import com.example.bakendorderwhatsapp.domain.service.ProductCatalog
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import io.ktor.http.path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class ProductApiClient(
    private val httpClient: HttpClient,
    private val apiConfig: ApiEnvironmentConfig,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : ProductCatalog {

    private val log = LoggerFactory.getLogger(ProductApiClient::class.java)

    override suspend fun searchByName(
        establishmentId: String,
        name: String,
        pageNumber: Int,
        pageSize: Int
    ): List<ProductSummary> {
        val query = name.trim()
        require(query.isNotEmpty()) { "Product name filter cannot be empty" }

        return runCatching {
            val response = httpClient.get {
                url {
                    protocol = URLProtocol.HTTPS
                    host = apiConfig.businessesHost
                    path(apiConfig.basePath, "Product")
                }
                parameter("filter.pageNumber", pageNumber)
                parameter("filter.pageSize", pageSize.coerceAtMost(10))
                parameter("filter.name", query)
                if (establishmentId.isNotBlank()) {
                    parameter("filter.establishmentId", establishmentId)
                }
            }

            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                log.warn("Product API error {}: {}", response.status, body.take(400))
                return emptyList()
            }

            json.decodeFromString<ProductPageDto>(body)
                .items
                .map {
                    ProductSummary(
                        id = it.id.orEmpty(),
                        name = it.name.orEmpty(),
                        price = it.price ?: 0.0,
                        stock = it.stock ?: 0
                    )
                }
                .filter { it.id.isNotBlank() && it.name.isNotBlank() }
                .take(10)
        }.getOrElse {
            log.error("Failed searching products name='{}'", query, it)
            emptyList()
        }
    }
}

@Serializable
private data class ProductPageDto(
    val items: List<ProductItemDto> = emptyList()
)

@Serializable
private data class ProductItemDto(
    val id: String? = null,
    val name: String? = null,
    val price: Double? = null,
    val stock: Int? = null
)
