package com.ygocardscanner.data.catalog

import com.ygocardscanner.data.catalog.yugioh.YgoJsonGermanPrintingParser

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YgoJsonGermanPrintingSourceTest {
    @Test
    fun `parser combines German locale prefix with printing suffix and resolves passcode`() {
        val records = YgoJsonGermanPrintingParser.parse(ByteArrayInputStream(archive(
            cards = """[{"id":"card-1","passwords":["53932291"]}]""",
            sets = """
                [{
                  "id":"set-1",
                  "name":{"en":"High-Speed Riders","de":"HIGH-SPEED RIDERS"},
                  "locales":{"de":{"prefix":"HSRD-DE"}},
                  "contents":[{
                    "locales":["de"],
                    "editions":["1st"],
                    "cards":[{"id":"printing-6","card":"card-1","suffix":"006","rarity":"super"}]
                  }]
                }]
            """.trimIndent(),
        )))

        val printing = records.single()
        assertEquals("53932291", printing.passcode)
        assertEquals("HSRD-DE006", printing.setCode)
        assertEquals("HIGH-SPEED RIDERS", printing.setName)
        assertEquals("Super Rare", printing.rarityCode)
        assertEquals("first_edition", printing.editionCode)
    }

    @Test
    fun `parser excludes non German locale printings`() {
        val records = YgoJsonGermanPrintingParser.parse(ByteArrayInputStream(archive(
            cards = """[{"id":"card-1","passwords":["53932291"]}]""",
            sets = """[{"id":"set-1","locales":{"en":{"prefix":"HSRD-EN"}},"contents":[]}]""",
        )))

        assertTrue(records.isEmpty())
    }

    private fun archive(cards: String, sets: String): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("cards.json"))
            zip.write(cards.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("sets.json"))
            zip.write(sets.toByteArray())
            zip.closeEntry()
        }
        output.toByteArray()
    }
}
