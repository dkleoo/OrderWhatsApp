package com.example.bakendorderwhatsapp.data.repository

import com.example.bakendorderwhatsapp.data.dataBase.cartItem.dao.CartItemDao
import com.example.bakendorderwhatsapp.domain.model.CartItem
import com.example.bakendorderwhatsapp.domain.repository.CartItemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CartItemRepositoryImpl(
    private val dao: CartItemDao
) : CartItemRepository {

    override suspend fun addOrIncrement(item: CartItem): CartItem = withContext(Dispatchers.IO) {
        dao.addOrIncrement(item)
    }

    override suspend fun listBySenderAndPhoneId(senderPhone: String, phoneId: String): List<CartItem> =
        withContext(Dispatchers.IO) {
            dao.findBySenderAndPhoneId(senderPhone, phoneId)
        }

    override suspend fun deleteBySenderAndPhoneId(senderPhone: String, phoneId: String): Int =
        withContext(Dispatchers.IO) {
            dao.deleteBySenderAndPhoneId(senderPhone, phoneId)
        }

    override suspend fun updateCheckoutInfo(
        senderPhone: String,
        phoneId: String,
        deliveryAddress: String?,
        paymentMethod: String?
    ): Int = withContext(Dispatchers.IO) {
        dao.updateCheckoutInfo(senderPhone, phoneId, deliveryAddress, paymentMethod)
    }
}
