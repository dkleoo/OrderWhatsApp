package com.example.bakendorderwhatsapp.data.repository

import com.example.bakendorderwhatsapp.data.dataBase.order.dao.OrderDao
import com.example.bakendorderwhatsapp.domain.model.OrderHeader
import com.example.bakendorderwhatsapp.domain.repository.OrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OrderRepositoryImpl(
    private val dao: OrderDao
) : OrderRepository {

    override suspend fun addOrIncrementDetail(
        senderPhone: String,
        phoneId: String,
        establishmentId: String,
        productId: String,
        productName: String,
        quantity: Int,
        unitPrice: Double
    ): OrderHeader = withContext(Dispatchers.IO) {
        dao.addOrIncrementDetail(
            senderPhone = senderPhone,
            phoneId = phoneId,
            establishmentId = establishmentId,
            productId = productId,
            productName = productName,
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    override suspend fun findDraft(senderPhone: String, phoneId: String): OrderHeader? =
        withContext(Dispatchers.IO) {
            dao.findDraftWithDetails(senderPhone, phoneId)
        }

    override suspend fun updateDraftCheckout(
        senderPhone: String,
        phoneId: String,
        customerName: String?,
        deliveryAddress: String?,
        paymentMethod: String?
    ): OrderHeader? = withContext(Dispatchers.IO) {
        dao.updateDraftCheckout(
            senderPhone = senderPhone,
            phoneId = phoneId,
            customerName = customerName,
            deliveryAddress = deliveryAddress,
            paymentMethod = paymentMethod
        )
    }

    override suspend fun completeDraft(
        senderPhone: String,
        phoneId: String,
        customerName: String,
        deliveryAddress: String,
        paymentMethod: String
    ): OrderHeader? = withContext(Dispatchers.IO) {
        dao.completeDraft(
            senderPhone = senderPhone,
            phoneId = phoneId,
            customerName = customerName,
            deliveryAddress = deliveryAddress,
            paymentMethod = paymentMethod
        )
    }

    override suspend fun deleteDraft(senderPhone: String, phoneId: String): Int =
        withContext(Dispatchers.IO) {
            dao.deleteDraftBySenderAndPhoneId(senderPhone, phoneId)
        }
}
