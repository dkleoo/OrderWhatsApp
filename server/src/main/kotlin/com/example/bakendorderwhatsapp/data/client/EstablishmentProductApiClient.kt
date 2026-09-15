package com.example.bakendorderwhatsapp.data.client

import com.example.bakendorderwhatsapp.config.ApiEnvironmentConfig
import com.example.bakendorderwhatsapp.domain.model.StoredProduct
import com.example.bakendorderwhatsapp.domain.service.EstablishmentProductSync
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
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

class EstablishmentProductApiClient(
    private val httpClient: HttpClient,
    private val apiConfig: ApiEnvironmentConfig,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : EstablishmentProductSync {

    private val log = LoggerFactory.getLogger(EstablishmentProductApiClient::class.java)

    override suspend fun fetchAllProducts(
        accessToken: String,
        establishmentId: String
    ): List<StoredProduct> {
        require(accessToken.isNotBlank()) { "Bearer token required for establishment products" }
        require(establishmentId.isNotBlank()) { "establishmentId required" }

        val all = mutableListOf<StoredProduct>()
        var page = 1
        val pageSize = 100

        while (true) {
            val response = httpClient.get {
                url {
                    protocol = URLProtocol.HTTPS
                    host = apiConfig.businessesHost
                    path(apiConfig.basePath, "establishment", establishmentId, "product")
                }
                bearerAuth(accessToken)
                parameter("filter.pageNumber", page)
                parameter("filter.pageSize", pageSize)
            }

            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                log.warn(
                    "Establishment products API error {} establishmentId={} page={}: {}",
                    response.status,
                    establishmentId,
                    page,
                    body.take(400)
                )
                break
            }

            val parsed = json.decodeFromString<ProductPageDto>(body)
            val mapped = parsed.items.mapNotNull { item ->
                val id = item.id?.trim().orEmpty()
                val name = item.name?.trim().orEmpty()
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                StoredProduct(
                    id = id,
                    establishmentId = establishmentId,
                    name = name,
                    price = item.price ?: 0.0,
                    stock = item.stock ?: 0
                )
            }
            all += mapped

            log.info(
                "Fetched {} products page={} establishmentId={} hasNext={}",
                mapped.size,
                page,
                establishmentId,
                parsed.hasNextPage
            )

            if (parsed.hasNextPage != true || mapped.isEmpty()) break
            page += 1
            if (page > 50) break
        }

        return all
    }
}

@Serializable
private data class ProductPageDto(
    val items: List<ProductItemDto> = emptyList(),
    val hasNextPage: Boolean? = false,
    val pageNumber: Int? = null,
    val totalPages: Int? = null
)

@Serializable
private data class ProductItemDto(
    val id: String? = null,
    val name: String? = null,
    val price: Double? = null,
    val stock: Int? = null
)
