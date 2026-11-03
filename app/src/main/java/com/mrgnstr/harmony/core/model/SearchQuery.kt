package com.mrgnstr.harmony.core.model

import java.text.Normalizer
import java.util.Locale

/** User input is literal words, never FTS operators or SQL patterns. */
object SearchQuery {
    private val words = Regex("""[\p{L}\p{N}]+""")
    private val accents = Regex("""\p{M}+""")

    fun tokens(text: String): List<String> = words.findAll(
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(accents, "").lowercase(Locale.ROOT),
    ).map { it.value }.toList()

    fun match(text: String): String? = tokens(text).takeIf { it.isNotEmpty() }
        ?.joinToString(" ") { "\"$it*\"" }

    fun matches(text: String, query: String): Boolean {
        val wanted = tokens(query)
        val actual = tokens(text)
        return wanted.isNotEmpty() && wanted.all { prefix -> actual.any { it.startsWith(prefix) } }
    }

    fun recentKey(text: String): String = text.trim().replace(Regex("""\s+"""), " ").lowercase(Locale.ROOT)
}
