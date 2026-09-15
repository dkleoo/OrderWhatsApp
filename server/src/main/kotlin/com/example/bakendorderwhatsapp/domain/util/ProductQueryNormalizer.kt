package com.example.bakendorderwhatsapp.domain.util

object ProductQueryNormalizer {

    private val stopWords = setOf(
        "tienes", "tiene", "tienen", "hay", "habrá", "habra",
        "me", "das", "dame", "deme", "quiero", "quisiera", "busco", "buscar",
        "necesito", "necesita", "por", "favor", "pf", "pls", "please",
        "un", "una", "unos", "unas", "el", "la", "los", "las",
        "de", "del", "para", "con", "sin", "y", "o", "en", "al",
        "mi", "tu", "su", "este", "esta", "ese", "esa", "eso",
        "producto", "productos", "algo", "algun", "algún", "alguna",
        "vendes", "vende", "venden", "traeme", "tráeme",
        "puedes", "puede", "pueden", "pasar", "pasame", "pásame"
    )

    /**
     * "tienes leche?" -> "leche"
     */
    fun normalize(raw: String): String {
        val cleaned = raw
            .lowercase()
            .replace(Regex("[¿?¡!.,;:\"'`´]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isBlank()) return ""

        val tokens = cleaned
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it in stopWords }

        return tokens.joinToString(" ").trim()
    }
}
