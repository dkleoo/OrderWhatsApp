package com.example.bakendorderwhatsapp.data.dataBase.order.dao

import com.example.bakendorderwhatsapp.data.dataBase.order.table.OrderDetailTable
import com.example.bakendorderwhatsapp.data.dataBase.order.table.OrderHeaderTable
import com.example.bakendorderwhatsapp.domain.model.OrderDetail
import com.example.bakendorderwhatsapp.domain.model.OrderHeader
import com.example.bakendorderwhatsapp.domain.model.OrderStatus
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

class OrderDao {

    fun addOrIncrementDetail(
        senderPhone: String,
        phoneId: String,
        establishmentId: String,
        productId: String,
        productName: String,
        quantity: Int,
        unitPrice: Double
    ): OrderHeader = transaction {
        val header = findDraftHeader(senderPhone, phoneId)
            ?: createDraftHeader(senderPhone, phoneId, establishmentId)

        val headerId = header.id!!
        val existing = OrderDetailTable
            .selectAll()
            .where {
                (OrderDetailTable.orderHeaderId eq headerId) and
                    (OrderDetailTable.productId eq productId)
            }
            .singleOrNull()

        if (existing != null) {
            val newQty = existing[OrderDetailTable.quantity] + quantity
            val lineTotal = unitPrice * newQty
            OrderDetailTable.update({ OrderDetailTable.id eq existing[OrderDetailTable.id] }) {
                it[OrderDetailTable.quantity] = newQty
                it[OrderDetailTable.unitPrice] = unitPrice
                it[OrderDetailTable.productName] = productName
                it[OrderDetailTable.lineTotal] = lineTotal
            }
        } else {
            OrderDetailTable.insert {
                it[id] = UUID.randomUUID()
                it[orderHeaderId] = headerId
                it[OrderDetailTable.productId] = productId
                it[OrderDetailTable.productName] = productName
                it[OrderDetailTable.quantity] = quantity
                it[OrderDetailTable.unitPrice] = unitPrice
                it[lineTotal] = unitPrice * quantity
            }
        }

        recalculateTotal(headerId)
        requireNotNull(findHeaderWithDetails(headerId))
    }

    fun findDraftWithDetails(senderPhone: String, phoneId: String): OrderHeader? = transaction {
        val header = findDraftHeader(senderPhone, phoneId) ?: return@transaction null
        findHeaderWithDetails(header.id!!)
    }

    fun updateDraftCheckout(
        senderPhone: String,
        phoneId: String,
        customerName: String? = null,
        deliveryAddress: String? = null,
        paymentMethod: String? = null
    ): OrderHeader? = transaction {
        val header = findDraftHeader(senderPhone, phoneId) ?: return@transaction null
        val headerId = header.id!!
        OrderHeaderTable.update({ OrderHeaderTable.id eq headerId }) {
            if (!customerName.isNullOrBlank()) {
                it[OrderHeaderTable.customerName] = customerName
            }
            if (!deliveryAddress.isNullOrBlank()) {
                it[OrderHeaderTable.deliveryAddress] = deliveryAddress
            }
            if (!paymentMethod.isNullOrBlank()) {
                it[OrderHeaderTable.paymentMethod] = paymentMethod
            }
        }
        findHeaderWithDetails(headerId)
    }

    fun completeDraft(
        senderPhone: String,
        phoneId: String,
        customerName: String,
        deliveryAddress: String,
        paymentMethod: String
    ): OrderHeader? = transaction {
        val header = findDraftHeader(senderPhone, phoneId) ?: return@transaction null
        val headerId = header.id!!
        recalculateTotal(headerId)
        val now = System.currentTimeMillis()
        OrderHeaderTable.update({ OrderHeaderTable.id eq headerId }) {
            it[OrderHeaderTable.customerName] = customerName
            it[OrderHeaderTable.deliveryAddress] = deliveryAddress
            it[OrderHeaderTable.paymentMethod] = paymentMethod
            it[status] = OrderStatus.COMPLETED.name
            it[completedAt] = now
        }
        findHeaderWithDetails(headerId)
    }

    /** Deletes only incomplete (DRAFT) orders for this chat. Completed orders are kept. */
    fun deleteDraftBySenderAndPhoneId(senderPhone: String, phoneId: String): Int = transaction {
        val draftIds = OrderHeaderTable
            .selectAll()
            .where {
                (OrderHeaderTable.senderPhone eq senderPhone) and
                    (OrderHeaderTable.phoneId eq phoneId) and
                    (OrderHeaderTable.status eq OrderStatus.DRAFT.name)
            }
            .map { it[OrderHeaderTable.id] }

        if (draftIds.isEmpty()) return@transaction 0

        draftIds.forEach { headerId ->
            OrderDetailTable.deleteWhere { OrderDetailTable.orderHeaderId eq headerId }
        }
        OrderHeaderTable.deleteWhere {
            (OrderHeaderTable.senderPhone eq senderPhone) and
                (OrderHeaderTable.phoneId eq phoneId) and
                (OrderHeaderTable.status eq OrderStatus.DRAFT.name)
        }
    }

    private fun findDraftHeader(senderPhone: String, phoneId: String): OrderHeader? =
        OrderHeaderTable
            .selectAll()
            .where {
                (OrderHeaderTable.senderPhone eq senderPhone) and
                    (OrderHeaderTable.phoneId eq phoneId) and
                    (OrderHeaderTable.status eq OrderStatus.DRAFT.name)
            }
            .singleOrNull()
            ?.toHeaderDomain(emptyList())

    private fun createDraftHeader(
        senderPhone: String,
        phoneId: String,
        establishmentId: String
    ): OrderHeader {
        val insertedId = OrderHeaderTable.insert {
            it[id] = UUID.randomUUID()
            it[OrderHeaderTable.senderPhone] = senderPhone
            it[OrderHeaderTable.phoneId] = phoneId
            it[OrderHeaderTable.establishmentId] = establishmentId
            it[customerName] = ""
            it[deliveryAddress] = ""
            it[paymentMethod] = ""
            it[total] = 0.0
            it[status] = OrderStatus.DRAFT.name
            it[createdAt] = System.currentTimeMillis()
            it[completedAt] = null
        } get OrderHeaderTable.id

        return OrderHeader(
            id = insertedId,
            senderPhone = senderPhone,
            phoneId = phoneId,
            establishmentId = establishmentId,
            status = OrderStatus.DRAFT
        )
    }

    private fun recalculateTotal(headerId: UUID) {
        val total = OrderDetailTable
            .selectAll()
            .where { OrderDetailTable.orderHeaderId eq headerId }
            .sumOf { it[OrderDetailTable.lineTotal] }
        OrderHeaderTable.update({ OrderHeaderTable.id eq headerId }) {
            it[OrderHeaderTable.total] = total
        }
    }

    private fun findHeaderWithDetails(headerId: UUID): OrderHeader? {
        val headerRow = OrderHeaderTable
            .selectAll()
            .where { OrderHeaderTable.id eq headerId }
            .singleOrNull()
            ?: return null
        val details = OrderDetailTable
            .selectAll()
            .where { OrderDetailTable.orderHeaderId eq headerId }
            .map { it.toDetailDomain() }
        return headerRow.toHeaderDomain(details)
    }

    private fun ResultRow.toHeaderDomain(details: List<OrderDetail>) = OrderHeader(
        id = this[OrderHeaderTable.id],
        senderPhone = this[OrderHeaderTable.senderPhone],
        phoneId = this[OrderHeaderTable.phoneId],
        establishmentId = this[OrderHeaderTable.establishmentId],
        customerName = this[OrderHeaderTable.customerName],
        deliveryAddress = this[OrderHeaderTable.deliveryAddress],
        paymentMethod = this[OrderHeaderTable.paymentMethod],
        total = this[OrderHeaderTable.total],
        status = runCatching { OrderStatus.valueOf(this[OrderHeaderTable.status]) }
            .getOrDefault(OrderStatus.DRAFT),
        createdAt = this[OrderHeaderTable.createdAt],
        completedAt = this[OrderHeaderTable.completedAt],
        details = details
    )

    private fun ResultRow.toDetailDomain() = OrderDetail(
        id = this[OrderDetailTable.id],
        orderHeaderId = this[OrderDetailTable.orderHeaderId],
        productId = this[OrderDetailTable.productId],
        productName = this[OrderDetailTable.productName],
        quantity = this[OrderDetailTable.quantity],
        unitPrice = this[OrderDetailTable.unitPrice],
        lineTotal = this[OrderDetailTable.lineTotal]
    )
}
