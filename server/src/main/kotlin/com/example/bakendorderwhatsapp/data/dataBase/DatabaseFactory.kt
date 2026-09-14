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
        val jdbcUrl = config.property("database.url").getString()
        val user = config.property("database.user").getString()
        val password = config.property("database.password").getString()
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
}

fun Application.configureDatabases() {
    DatabaseFactory.init(environment.config)
}
