package com.example.bakendorderwhatsapp.data.client

import com.example.bakendorderwhatsapp.config.ApiEnvironmentConfig
import com.example.bakendorderwhatsapp.domain.model.StoredProduct
import com.example.bakendorderwhatsapp.domain.service.EstablishmentProductSync
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
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
        val bearer = normalizeBearer(accessToken)
        require(bearer.isNotBlank()) { "Bearer token required for establishment products" }
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
                header(HttpHeaders.Authorization, "Bearer $bearer")
                // Compatible with common .NET filter bindings
                parameter("filter.pageNumber", page)
                parameter("filter.pageSize", pageSize)
                parameter("Filter.PageNumber", page)
                parameter("Filter.PageSize", pageSize)
                parameter("pageNumber", page)
                parameter("pageSize", pageSize)
            }

            val requestUrl =
                "https://${apiConfig.businessesHost}/${apiConfig.basePath}/establishment/$establishmentId/product?page=$page"
            val body = response.bodyAsText()

            if (!response.status.isSuccess()) {
                log.warn(
                    "Establishment products API error {} url={} body={}",
                    response.status,
                    requestUrl,
                    body.take(500)
                )
                break
            }

            val parsed = runCatching {
                json.decodeFromString<ProductPageDto>(body)
            }.getOrElse {
                log.error("Failed parsing products JSON for establishmentId={}: {}", establishmentId, it.message)
                log.warn("Body preview: {}", body.take(500))
                break
            }

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
                "Fetched {} products (page={}, totalSoFar={}, hasNext={}, totalCount={}) establishmentId={}",
                mapped.size,
                page,
                all.size,
                parsed.hasNextPage,
                parsed.totalCount,
                establishmentId
            )

            val hasMore = when {
                parsed.hasNextPage == true -> true
                parsed.totalPages != null && page < parsed.totalPages -> true
                mapped.size >= pageSize -> true
                else -> false
            }
            if (!hasMore || mapped.isEmpty()) break

            page += 1
            if (page > 50) break
        }

        return all
    }

    private fun normalizeBearer(token: String): String =
        token.trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()
}

@Serializable
private data class ProductPageDto(
    val items: List<ProductItemDto> = emptyList(),
    val hasNextPage: Boolean? = false,
    val pageNumber: Int? = null,
    val totalPages: Int? = null,
    val totalCount: Int? = null
)

@Serializable
private data class ProductItemDto(
    val id: String? = null,
    val name: String? = null,
    val price: Double? = null,
    val stock: Int? = null
)
