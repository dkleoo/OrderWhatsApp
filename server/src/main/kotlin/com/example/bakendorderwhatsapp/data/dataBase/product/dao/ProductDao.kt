package com.example.bakendorderwhatsapp.data.dataBase.product.dao

import com.example.bakendorderwhatsapp.data.dataBase.product.table.ProductTable
import com.example.bakendorderwhatsapp.domain.model.ProductSummary
import com.example.bakendorderwhatsapp.domain.model.StoredProduct
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ProductDao {

    fun replaceForEstablishment(establishmentId: String, products: List<StoredProduct>) = transaction {
        ProductTable.deleteWhere { ProductTable.establishmentId eq establishmentId }
        if (products.isEmpty()) return@transaction

        ProductTable.batchInsert(products) { product ->
            this[ProductTable.id] = product.id
            this[ProductTable.establishmentId] = product.establishmentId
            this[ProductTable.name] = product.name
            this[ProductTable.price] = product.price
            this[ProductTable.stock] = product.stock
        }
    }

    fun searchByName(
        establishmentId: String,
        name: String,
        limit: Int = 10
    ): List<ProductSummary> = transaction {
        val query = name.trim().lowercase()
        if (query.isEmpty()) return@transaction emptyList()

        ProductTable
            .selectAll()
            .where {
                (ProductTable.establishmentId eq establishmentId) and
                    (ProductTable.name.lowerCase() like "%$query%")
            }
            .limit(limit.coerceAtMost(10))
            .map {
                ProductSummary(
                    id = it[ProductTable.id],
                    name = it[ProductTable.name],
                    price = it[ProductTable.price],
                    stock = it[ProductTable.stock]
                )
            }
    }

    fun countByEstablishment(establishmentId: String): Int = transaction {
        ProductTable
            .selectAll()
            .where { ProductTable.establishmentId eq establishmentId }
            .count()
            .toInt()
    }
}
