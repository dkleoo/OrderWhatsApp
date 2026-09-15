package com.example.bakendorderwhatsapp.domain.util

/**
 * Distinguishes greetings / chitchat from product requests.
 * "buenos dias" → greeting; "hola tienes leche" → product query "leche".
 */
object ChatMessageIntent {

    private val greetingPhrases = listOf(
        "muy buenos dias",
        "muy buenos días",
        "muy buenas tardes",
        "muy buenas noches",
        "muy buenas",
        "buenos dias",
        "buenos días",
        "buen dia",
        "buen día",
        "buenas tardes",
        "buena tarde",
        "buenas noches",
        "buena noche",
        "que tal",
        "qué tal",
        "como estas",
        "cómo estás",
        "como esta",
        "cómo está",
        "como andas",
        "cómo andas",
        "hola que tal",
        "hola qué tal",
        "buenas",
        "buenass",
        "saludos",
        "hola",
        "holaa",
        "holaaa",
        "holi",
        "oli",
        "hey",
        "hi",
        "hello",
        "good morning",
        "good afternoon",
        "good evening"
    ).sortedByDescending { it.length }

    fun clean(raw: String): String =
        raw.lowercase()
            .replace(Regex("[¿?¡!.,;:\"'`´]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Product name to search, or null when the message is only a greeting / empty. */
    fun extractProductQuery(raw: String): String? {
        var text = clean(raw)
        if (text.isBlank()) return null

        if (greetingPhrases.any { text == it }) return null

        for (phrase in greetingPhrases) {
            if (text.startsWith("$phrase ")) {
                text = text.removePrefix(phrase).trim()
                break
            }
        }

        val query = ProductQueryNormalizer.normalize(text)
        return query.ifBlank { null }
    }

    fun isGreetingOnly(raw: String): Boolean {
        val text = clean(raw)
        if (text.isBlank()) return false
        return extractProductQuery(raw) == null && greetingPhrases.any { text == it || text.startsWith("$it ") }
    }
}
