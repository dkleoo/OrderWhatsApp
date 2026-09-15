package com.example.bakendorderwhatsapp.data.dataBase

import com.example.bakendorderwhatsapp.data.dataBase.cartItem.table.CartItemTable
import com.example.bakendorderwhatsapp.data.dataBase.chatSession.table.ChatSessionTable
import com.example.bakendorderwhatsapp.data.dataBase.whatsappSettings.table.WhatsAppSettingsTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.config.ApplicationConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI

object DatabaseFactory {

    fun init(config: ApplicationConfig, log: org.slf4j.Logger) {
        val connection = resolveConnection(config)
        log.info("Connecting to database at {}", redactUrl(connection.jdbcUrl))

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = connection.jdbcUrl
            username = connection.user
            password = connection.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = config.propertyOrNull("database.maxPoolSize")
                ?.getString()
                ?.toIntOrNull()
                ?: 5
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            connectionTimeout = 30_000
            validate()
        }

        try {
            Database.connect(HikariDataSource(hikariConfig))
            transaction {
                SchemaUtils.create(WhatsAppSettingsTable)
                SchemaUtils.create(ChatSessionTable)
                SchemaUtils.create(CartItemTable)
            }
            // Add new columns safely on existing deployments (won't crash startup).
            runCatching {
                transaction {
                    exec("ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS establishment_id VARCHAR(128) DEFAULT '';")
                    exec("ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS establishment_name VARCHAR(255) DEFAULT '';")
                    exec("ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS flow_state VARCHAR(64) DEFAULT 'AWAITING_PRODUCT_NAME';")
                    exec("ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS pending_product_id VARCHAR(128) DEFAULT '';")
                    exec("ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS pending_product_name VARCHAR(255) DEFAULT '';")
                    exec("ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS pending_product_price DOUBLE PRECISION DEFAULT 0;")
                    exec("ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS pending_product_stock INTEGER DEFAULT 0;")
                    exec("ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS delivery_address TEXT DEFAULT '';")
                    exec("ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS payment_method VARCHAR(64) DEFAULT '';")
                    exec("ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS last_search_json TEXT DEFAULT '';")
                    exec("ALTER TABLE whatsapp_settings DROP COLUMN IF EXISTS whatsapp_token;")
                }
            }.onFailure {
                log.warn("DB column migration skipped/failed: {}", it.message)
            }
            log.info("Database ready")
        } catch (e: Exception) {
            throw IllegalStateException(
                "Database connection failed for ${redactUrl(connection.jdbcUrl)}. " +
                    "Check DATABASE_URL (internal host), DATABASE_USER and DATABASE_PASSWORD. " +
                    "Root cause: ${e.message}",
                e
            )
        }
    }

    private fun resolveConnection(config: ApplicationConfig): DbConnection {
        val rawUrl = System.getenv("DATABASE_URL")
            ?: config.property("database.url").getString()

        val parsed = parseUrl(rawUrl)

        val user = System.getenv("DATABASE_USER")
            ?: parsed.user
            ?: config.property("database.user").getString()

        val password = System.getenv("DATABASE_PASSWORD")
            ?: parsed.password
            ?: config.propertyOrNull("database.password")?.getString().orEmpty()

        require(user.isNotBlank()) {
            "DATABASE_USER is missing. Set it in Render → Environment."
        }
        require(password.isNotBlank()) {
            "DATABASE_PASSWORD is missing. Set it in Render → Environment."
        }

        return DbConnection(
            jdbcUrl = withSsl(parsed.jdbcUrl),
            user = user,
            password = password
        )
    }

    private fun parseUrl(raw: String): ParsedUrl {
        val normalized = when {
            raw.startsWith("jdbc:postgresql://") -> raw.removePrefix("jdbc:")
            raw.startsWith("postgres://") -> raw.replaceFirst("postgres://", "postgresql://")
            raw.startsWith("postgresql://") -> raw
            raw.startsWith("jdbc:postgresql:") -> raw.removePrefix("jdbc:")
            else -> raw
        }

        // Plain host form: jdbc already handled; host:port/db without scheme
        if (!normalized.startsWith("postgresql://")) {
            return ParsedUrl(jdbcUrl = "jdbc:postgresql://$normalized", user = null, password = null)
        }

        val uri = URI(normalized)
        val userInfo = uri.userInfo
        val user = userInfo?.substringBefore(":")?.takeIf { it.isNotBlank() }
        val password = userInfo
            ?.substringAfter(":", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }

        val host = uri.host ?: error("DATABASE_URL has no host")
        val port = if (uri.port > 0) uri.port else 5432
        val database = uri.path.trimStart('/').substringBefore('?').ifBlank {
            error("DATABASE_URL has no database name")
        }
        val query = uri.query

        val jdbc = buildString {
            append("jdbc:postgresql://")
            append(host)
            append(':')
            append(port)
            append('/')
            append(database)
            if (!query.isNullOrBlank()) {
                append('?')
                append(query)
            }
        }

        return ParsedUrl(jdbcUrl = jdbc, user = user, password = password)
    }

    private fun withSsl(jdbcUrl: String): String {
        if (jdbcUrl.contains("sslmode=", ignoreCase = true)) return jdbcUrl
        return if (jdbcUrl.contains('?')) {
            "$jdbcUrl&sslmode=require"
        } else {
            "$jdbcUrl?sslmode=require"
        }
    }

    private fun redactUrl(url: String): String =
        url.replace(Regex("://([^:/]+):([^@/]+)@"), "://$1:***@")

    private data class ParsedUrl(
        val jdbcUrl: String,
        val user: String?,
        val password: String?
    )

    private data class DbConnection(
        val jdbcUrl: String,
        val user: String,
        val password: String
    )
}

fun Application.configureDatabases() {
    DatabaseFactory.init(environment.config, log)
}
