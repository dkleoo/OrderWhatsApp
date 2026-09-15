package com.example.bakendorderwhatsapp.data.dataBase.product.table

import org.jetbrains.exposed.sql.Table

object ProductTable : Table("products") {
    val id = varchar("id", 128)
    val establishmentId = varchar("establishment_id", 128)
    val name = varchar("name", 255)
    val price = double("price")
    val stock = integer("stock")

    override val primaryKey = PrimaryKey(id)
}
