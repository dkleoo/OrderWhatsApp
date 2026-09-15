package com.example.bakendorderwhatsapp.config

data class ApiEnvironmentConfig(
    val authenticationHost: String,
    val businessesHost: String,
    val whatsappOrdersHost: String,
    val basePath: String
)

object ApiEnvironments {

    private const val WHATSAPP_ORDERS_HOST = "orderwhatsapp.onrender.com"

    val DEVELOPMENT = ApiEnvironmentConfig(
        authenticationHost = "authentication-api-dev-hzafheefa9hwajec.centralus-01.azurewebsites.net",
        businessesHost = "wsbackendtiendason-dev-ccevh5hza6egbrgx.centralus-01.azurewebsites.net",
        whatsappOrdersHost = WHATSAPP_ORDERS_HOST,
        basePath = "api"
    )

    fun resolve(name: String?): ApiEnvironmentConfig = when (name?.lowercase()) {
        "prod", "production" -> DEVELOPMENT // replace when production hosts exist
        else -> DEVELOPMENT
    }
}
