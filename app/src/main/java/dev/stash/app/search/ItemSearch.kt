package dev.stash.app.search

import dev.stash.app.data.Item

/**
 * Turns "where's my passport?" / "где мой паспорт" into a ranked list of items.
 *
 * Pure Kotlin, fully offline, instant. Filler words are dropped, and words are
 * compared by prefix so "passports" matches "passport" and "паспорта" matches
 * "паспорт" (a cheap stand-in for stemming).
 */
object ItemSearch {

    private val splitter = Regex("[^\\p{L}\\p{N}]+")

    private val stopWords = setOf(
        // English
        "where", "wheres", "s", "is", "are", "the", "a", "an", "my", "our", "did", "i",
        "put", "leave", "left", "keep", "kept", "store", "stored", "find", "can", "t", "cant",
        "do", "have", "it", "to", "of", "in", "on", "at", "for", "what", "about",
        // Russian
        "где", "мой", "моя", "моё", "мое", "мои", "лежит", "лежат", "я", "положил", "положила",
        "найти", "не", "могу", "это", "в", "на", "у", "с", "куда",
    )

    fun tokens(text: String): List<String> =
        text.lowercase()
            .split(splitter)
            .filter { it.length > 1 && it !in stopWords }

    fun search(query: String, items: List<Item>): List<Item> {
        val queryTokens = tokens(query)
        if (queryTokens.isEmpty()) return emptyList()

        return items
            .map { it to score(queryTokens, it) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<Item, Int>> { it.second }
                    .thenByDescending { it.first.updatedAt },
            )
            .map { it.first }
    }

    private fun score(queryTokens: List<String>, item: Item): Int {
        val nameWords = tokens(item.name)
        val tagWords = tokens(item.tags)
        val placeWords = tokens(item.place)
        val otherWords = tokens(item.note) + tokens(item.lentTo.orEmpty())

        return queryTokens.sumOf { q ->
            when {
                nameWords.any { matches(q, it) } -> 5
                tagWords.any { matches(q, it) } -> 3
                placeWords.any { matches(q, it) } -> 2
                otherWords.any { matches(q, it) } -> 1
                else -> 0
            }
        }
    }

    /**
     * Exact match, or the words differ only by a short ending:
     * key/keys, passport/passports, ключ/ключи, паспорт/паспорта — but not pen/pencil.
     */
    internal fun matches(a: String, b: String): Boolean {
        if (a == b) return true
        val shorter = minOf(a.length, b.length)
        val longer = maxOf(a.length, b.length)
        val common = a.commonPrefixWith(b).length
        return common >= 3 && common >= shorter - 1 && longer - common <= 2
    }
}
