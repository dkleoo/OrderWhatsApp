package com.example.bakendorderwhatsapp.data.dataBase.whatsappSettings.table

import org.jetbrains.exposed.sql.Table

object WhatsAppSettingsTable : Table("whatsapp_settings") {
    val id = uuid("id").autoGenerate()
    val businessId = varchar("business_id", 128)
    val establishmentId = varchar("establishment_id", 128)
    val establishmentName = varchar("establishment_name", 255)
    val whatsappPhone = varchar("whatsapp_phone", 32)
    val phoneId = varchar("phone_id", 128)
    val whatsappBusinessId = varchar("whatsapp_business_id", 128)
    val token = text("token")
    val whatsappToken = text("whatsapp_token").default("")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(businessId, establishmentId)
    }
}
