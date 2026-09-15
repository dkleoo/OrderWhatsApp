package com.example.bakendorderwhatsapp.domain.model

data class StoredProduct(
    val id: String,
    val establishmentId: String,
    val name: String,
    val price: Double,
    val stock: Int
)
