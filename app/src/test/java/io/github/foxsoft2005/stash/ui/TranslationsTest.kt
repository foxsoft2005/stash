package io.github.foxsoft2005.stash.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Every translation must match the English resources: same names, same
 * placeholders (%1$s, %d…), and every plural form the language needs.
 * Lint catches some of this; this test catches all of it, with clear messages.
 */
class TranslationsTest {

    private val res = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }

    private data class Strings(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun load(folder: String): Strings {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings.xml"))
        val strings = mutableMapOf<String, String>()
        val plurals = mutableMapOf<String, Map<String, String>>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            if (e.getAttribute("translatable") == "false") continue
            when (e.tagName) {
                "string" -> strings[e.getAttribute("name")] = e.textContent
                "plurals" -> {
                    val items = e.getElementsByTagName("item")
                    plurals[e.getAttribute("name")] = (0 until items.length).associate {
                        val item = items.item(it) as Element
                        item.getAttribute("quantity") to item.textContent
                    }
                }
            }
        }
        return Strings(strings, plurals)
    }

    private val placeholder = Regex("""%(\d+\$)?[sd]""")
    private fun placeholders(text: String) = placeholder.findAll(text).map { it.value }.sorted().toList()

    private val english = load("values")
    private val translations = mapOf(
        "ru" to (load("values-ru") to setOf("one", "few", "many", "other")),
    )

    @Test fun `every translation has exactly the English string names`() {
        for ((lang, pair) in translations) {
            val t = pair.first
            assertEquals("$lang: strings", english.strings.keys.sorted(), t.strings.keys.sorted())
            assertEquals("$lang: plurals", english.plurals.keys.sorted(), t.plurals.keys.sorted())
        }
    }

    @Test fun `placeholders match the English text`() {
        for ((lang, pair) in translations) {
            val t = pair.first
            for ((name, en) in english.strings) {
                assertEquals("$lang: $name", placeholders(en), placeholders(t.strings.getValue(name)))
            }
            // Every plural form has the same placeholders as the English "other" form
            // (the count as %d or %1$d, then any extra arguments such as %2$s).
            for ((name, forms) in t.plurals) {
                val expected = placeholders(english.plurals.getValue(name).getValue("other"))
                forms.forEach { (q, text) ->
                    assertEquals("$lang: $name[$q] placeholders", expected, placeholders(text))
                }
            }
        }
    }

    @Test fun `every plural has all the forms its language needs`() {
        english.plurals.forEach { (name, forms) ->
            assertEquals("en: $name", setOf("one", "other"), forms.keys)
        }
        for ((lang, pair) in translations) {
            val (t, needed) = pair
            t.plurals.forEach { (name, forms) -> assertEquals("$lang: $name", needed, forms.keys) }
        }
    }

    @Test fun `no text is left untranslated by accident`() {
        for ((lang, pair) in translations) {
            val same = english.strings.filter { (name, en) -> pair.first.strings[name] == en }.keys
            assertTrue("$lang: identical to English: $same", same.isEmpty())
        }
    }
}
