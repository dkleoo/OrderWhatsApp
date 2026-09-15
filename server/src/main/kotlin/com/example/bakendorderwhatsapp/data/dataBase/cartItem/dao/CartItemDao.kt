package com.example.bakendorderwhatsapp.data.dataBase.cartItem.dao

import com.example.bakendorderwhatsapp.data.dataBase.cartItem.table.CartItemTable
import com.example.bakendorderwhatsapp.domain.model.CartItem
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

class CartItemDao {

    fun addOrIncrement(item: CartItem): CartItem = transaction {
        val existing = CartItemTable
            .selectAll()
            .where {
                (CartItemTable.senderPhone eq item.senderPhone) and
                    (CartItemTable.phoneId eq item.phoneId) and
                    (CartItemTable.productId eq item.productId)
            }
            .singleOrNull()

        if (existing != null) {
            val id = existing[CartItemTable.id]
            val newQty = existing[CartItemTable.quantity] + item.quantity
            CartItemTable.update({ CartItemTable.id eq id }) {
                it[quantity] = newQty
                it[price] = item.price
                it[productName] = item.productName
            }
            CartItemTable
                .selectAll()
                .where { CartItemTable.id eq id }
                .single()
                .toDomain()
        } else {
            val insertedId = CartItemTable.insert {
                it[id] = item.id ?: UUID.randomUUID()
                it[senderPhone] = item.senderPhone
                it[phoneId] = item.phoneId
                it[establishmentId] = item.establishmentId
                it[productId] = item.productId
                it[productName] = item.productName
                it[quantity] = item.quantity
                it[price] = item.price
                it[deliveryAddress] = item.deliveryAddress
                it[paymentMethod] = item.paymentMethod
                it[createdAt] = item.createdAt
            } get CartItemTable.id

            CartItemTable
                .selectAll()
                .where { CartItemTable.id eq insertedId }
                .single()
                .toDomain()
        }
    }

    fun findBySenderAndPhoneId(senderPhone: String, phoneId: String): List<CartItem> = transaction {
        CartItemTable
            .selectAll()
            .where {
                (CartItemTable.senderPhone eq senderPhone) and
                    (CartItemTable.phoneId eq phoneId)
            }
            .map { it.toDomain() }
            // Por si hubiera filas viejas duplicadas, agrupar visualmente a nivel repo no; aquí devolvemos tal cual.
            // La lógica de incremento evita duplicados nuevos.
    }

    fun deleteBySenderAndPhoneId(senderPhone: String, phoneId: String): Int = transaction {
        CartItemTable.deleteWhere {
            (CartItemTable.senderPhone eq senderPhone) and
                (CartItemTable.phoneId eq phoneId)
        }
    }

    fun updateCheckoutInfo(
        senderPhone: String,
        phoneId: String,
        deliveryAddress: String?,
        paymentMethod: String?
    ): Int = transaction {
        CartItemTable.update({
            (CartItemTable.senderPhone eq senderPhone) and
                (CartItemTable.phoneId eq phoneId)
        }) {
            if (!deliveryAddress.isNullOrBlank()) {
                it[CartItemTable.deliveryAddress] = deliveryAddress
            }
            if (!paymentMethod.isNullOrBlank()) {
                it[CartItemTable.paymentMethod] = paymentMethod
            }
        }
    }

    private fun ResultRow.toDomain() = CartItem(
        id = this[CartItemTable.id],
        senderPhone = this[CartItemTable.senderPhone],
        phoneId = this[CartItemTable.phoneId],
        establishmentId = this[CartItemTable.establishmentId],
        productId = this[CartItemTable.productId],
        productName = this[CartItemTable.productName],
        quantity = this[CartItemTable.quantity],
        price = this[CartItemTable.price],
        deliveryAddress = this[CartItemTable.deliveryAddress],
        paymentMethod = this[CartItemTable.paymentMethod],
        createdAt = this[CartItemTable.createdAt]
    )
}
