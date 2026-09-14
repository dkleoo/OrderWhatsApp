package com.example.bakendorderwhatsapp.presentation.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponseDto(
    val error: String
)
