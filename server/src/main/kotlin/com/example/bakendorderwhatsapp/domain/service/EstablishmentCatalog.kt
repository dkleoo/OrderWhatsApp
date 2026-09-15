package com.example.bakendorderwhatsapp.domain.service

interface EstablishmentCatalog {
    suspend fun getEstablishmentName(establishmentId: String): String?
}
