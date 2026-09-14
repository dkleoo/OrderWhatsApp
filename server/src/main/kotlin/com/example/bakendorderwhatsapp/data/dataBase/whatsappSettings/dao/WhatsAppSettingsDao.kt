package com.example.bakendorderwhatsapp.data.dataBase.whatsappSettings.dao

import com.example.bakendorderwhatsapp.data.dataBase.whatsappSettings.table.WhatsAppSettingsTable
import com.example.bakendorderwhatsapp.domain.model.WhatsAppSettings
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

class WhatsAppSettingsDao {

    fun findByBusinessAndEstablishment(
        businessId: String,
        establishmentId: String
    ): WhatsAppSettings? = transaction {
        WhatsAppSettingsTable
            .selectAll()
            .where {
                (WhatsAppSettingsTable.businessId eq businessId) and
                    (WhatsAppSettingsTable.establishmentId eq establishmentId)
            }
            .singleOrNull()
            ?.toDomain()
    }

    fun findByBusinessId(businessId: String): List<WhatsAppSettings> = transaction {
        WhatsAppSettingsTable
            .selectAll()
            .where { WhatsAppSettingsTable.businessId eq businessId }
            .map { it.toDomain() }
    }

    fun upsert(settings: WhatsAppSettings): WhatsAppSettings = transaction {
        val existing = WhatsAppSettingsTable
            .selectAll()
            .where {
                (WhatsAppSettingsTable.businessId eq settings.businessId) and
                    (WhatsAppSettingsTable.establishmentId eq settings.establishmentId)
            }
            .singleOrNull()

        if (existing != null) {
            val id = existing[WhatsAppSettingsTable.id]
            WhatsAppSettingsTable.update({ WhatsAppSettingsTable.id eq id }) {
                it[establishmentName] = settings.establishmentName
                it[whatsappPhone] = settings.whatsappPhone
                it[phoneId] = settings.phoneId
                it[whatsappBusinessId] = settings.whatsappBusinessId
                it[token] = settings.token
            }
            findById(id)!!
        } else {
            val insertedId = WhatsAppSettingsTable.insert {
                it[id] = settings.id ?: UUID.randomUUID()
                it[businessId] = settings.businessId
                it[establishmentId] = settings.establishmentId
                it[establishmentName] = settings.establishmentName
                it[whatsappPhone] = settings.whatsappPhone
                it[phoneId] = settings.phoneId
                it[whatsappBusinessId] = settings.whatsappBusinessId
                it[token] = settings.token
            } get WhatsAppSettingsTable.id

            findById(insertedId)!!
        }
    }

    private fun findById(id: UUID): WhatsAppSettings? =
        WhatsAppSettingsTable
            .selectAll()
            .where { WhatsAppSettingsTable.id eq id }
            .singleOrNull()
            ?.toDomain()

    private fun ResultRow.toDomain() = WhatsAppSettings(
        id = this[WhatsAppSettingsTable.id],
        businessId = this[WhatsAppSettingsTable.businessId],
        establishmentId = this[WhatsAppSettingsTable.establishmentId],
        establishmentName = this[WhatsAppSettingsTable.establishmentName],
        whatsappPhone = this[WhatsAppSettingsTable.whatsappPhone],
        phoneId = this[WhatsAppSettingsTable.phoneId],
        whatsappBusinessId = this[WhatsAppSettingsTable.whatsappBusinessId],
        token = this[WhatsAppSettingsTable.token]
    )
}
