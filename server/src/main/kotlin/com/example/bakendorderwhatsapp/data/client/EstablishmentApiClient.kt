package com.example.bakendorderwhatsapp.data.client

import com.example.bakendorderwhatsapp.config.ApiEnvironmentConfig
import com.example.bakendorderwhatsapp.domain.service.EstablishmentCatalog
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import io.ktor.http.path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class EstablishmentApiClient(
    private val httpClient: HttpClient,
    private val apiConfig: ApiEnvironmentConfig,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : EstablishmentCatalog {

    private val log = LoggerFactory.getLogger(EstablishmentApiClient::class.java)

    override suspend fun getEstablishmentName(establishmentId: String): String? {
        if (establishmentId.isBlank()) return null

        return runCatching {
            val response = httpClient.get {
                url {
                    protocol = URLProtocol.HTTPS
                    host = apiConfig.businessesHost
                    path(apiConfig.basePath, "establishment", establishmentId)
                }
            }

            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                log.warn(
                    "Establishment API error {} for id={}: {}",
                    response.status,
                    establishmentId,
                    body.take(300)
                )
                return null
            }

            json.decodeFromString<EstablishmentResponseDto>(body).name?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrElse {
            log.error("Failed fetching establishment id={}", establishmentId, it)
            null
        }
    }
}

@Serializable
private data class EstablishmentResponseDto(
    val name: String? = null
)
