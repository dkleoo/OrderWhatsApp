package com.example.bakendorderwhatsapp.data.dataBase

import com.example.bakendorderwhatsapp.data.dataBase.whatsappSettings.table.WhatsAppSettingsTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

    fun init(config: ApplicationConfig) {
        val jdbcUrl = normalizeJdbcUrl(
            System.getenv("DATABASE_URL")
                ?: config.property("database.url").getString()
        )
        val user = System.getenv("DATABASE_USER")
            ?: config.property("database.user").getString()
        val password = System.getenv("DATABASE_PASSWORD")
            ?: config.propertyOrNull("database.password")?.getString().orEmpty()

        require(password.isNotBlank()) {
            "DATABASE_PASSWORD is missing. Set it in Render → Environment."
        }

        val driver = config.propertyOrNull("database.driver")?.getString()
            ?: "org.postgresql.Driver"
        val maxPoolSize = config.propertyOrNull("database.maxPoolSize")?.getString()?.toIntOrNull()
            ?: 10

        val hikariConfig = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            this.driverClassName = driver
            this.maximumPoolSize = maxPoolSize
            this.isAutoCommit = false
            this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        Database.connect(HikariDataSource(hikariConfig))

        transaction {
            SchemaUtils.create(WhatsAppSettingsTable)
        }
    }

    private fun normalizeJdbcUrl(url: String): String =
        when {
            url.startsWith("jdbc:") -> url
            url.startsWith("postgres://") -> "jdbc:" + url.replaceFirst("postgres://", "postgresql://")
            url.startsWith("postgresql://") -> "jdbc:$url"
            else -> url
        }
}

fun Application.configureDatabases() {
    DatabaseFactory.init(environment.config)
}
