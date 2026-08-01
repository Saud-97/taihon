package eu.kanade.tachiyomi.util.lang

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StringExtensionsTest {

    @Test
    fun `normalizeApostrophe strict`() {
        val input = "Manga's Manga’s Manga‘s Mangaʼs Mangaʹs Manga′s Manga‛s Manga՚s Mangaꞌs Manga＇s"
        val expected = "Manga's Manga's Manga's Manga's Manga's Manga's Manga's Manga's Manga's Manga's"
        assertEquals(expected, input.normalizeApostrophe())
        assertEquals("Manga's", "Manga's".normalizeApostrophe())
        assertEquals("O'clock", "O'clock".normalizeApostrophe())
        assertEquals("Manga's", "Manga’s".normalizeApostrophe())

    }

    @Test
    fun `normalizeApostrophe fuzzy`() {
        assertEquals("Manga", "Manga's".normalizeApostrophe(fuzzy = true))
        assertEquals("O clock", "O'clock".normalizeApostrophe(fuzzy = true))
        assertEquals("Manga", "Manga’s".normalizeApostrophe(fuzzy = true))
    }

    @Test
    fun `chop`() {
        assertEquals("Manga…", "Manga Title".chop(6))
        assertEquals("Manga", "Manga".chop(10))
    }

    @Test
    fun `truncateCenter`() {
        assertEquals("Man...tle", "Manga Title".truncateCenter(9))
        assertEquals("Manga", "Manga".truncateCenter(10))
    }

    @Test
    fun `byteSize`() {
        assertEquals(5, "Manga".byteSize())
        assertEquals(8, "Manga’".byteSize()) // ’ is 3 bytes in UTF-8
    }

    @Test
    fun `takeBytes`() {
        assertEquals("Manga", "Manga Title".takeBytes(5))
        assertEquals("Manga", "Manga’".takeBytes(5)) // ’ is truncated if not enough bytes
        assertEquals(
            "Manga",
            "Manga’".takeBytes(6),
        ) // ’ is 3 bytes, taking 1 byte of it results in replacement char which is removed
    }

    @Test
    fun `compareToCaseInsensitiveNaturalOrder`() {
        assert("Manga 2".compareToCaseInsensitiveNaturalOrder("Manga 10") < 0)
        assert("Manga 10".compareToCaseInsensitiveNaturalOrder("Manga 2") > 0)
        assert("manga".compareToCaseInsensitiveNaturalOrder("MANGA") == 0)
    }
}
