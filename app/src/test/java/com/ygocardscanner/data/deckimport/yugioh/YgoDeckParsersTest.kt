package com.ygocardscanner.data.deckimport.yugioh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class YgoDeckParsersTest {
    @Test
    fun `parses YDK sections comments and duplicate passcodes`() {
        val document = YgoDeckParsers.parseYdk(
            sourceLabel = "Starter Deck.ydk",
            rawInput = """
                #created by EDOPro
                #main
                89631139
                89631139
                #extra
                45231177
                !side
                46986414
            """.trimIndent(),
        )

        assertEquals(listOf("89631139", "89631139"), document.cardsBySection.getValue(YgoDeckSection.MAIN))
        assertEquals(listOf("45231177"), document.cardsBySection.getValue(YgoDeckSection.EXTRA))
        assertEquals(listOf("46986414"), document.cardsBySection.getValue(YgoDeckSection.SIDE))
        assertEquals(4, document.totalCardCount)
    }

    @Test
    fun `parses YDKe little endian passcodes`() {
        val document = YgoDeckParsers.parseYdke(
            sourceLabel = "Copied YDKe",
            rawInput = "ydke://o6lXBQbqRAQ=!SSyyAg==!rvTMAg==!",
        )

        assertEquals(listOf("89631139", "71625222"), document.cardsBySection.getValue(YgoDeckSection.MAIN))
        assertEquals(listOf("45231177"), document.cardsBySection.getValue(YgoDeckSection.EXTRA))
        assertEquals(listOf("46986414"), document.cardsBySection.getValue(YgoDeckSection.SIDE))
    }

    @Test
    fun `rejects a card outside a deck section`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            YgoDeckParsers.parseYdk("broken.ydk", "89631139")
        }

        assertEquals("Line 1: card passcodes must appear under #main, #extra, or !side.", error.message)
    }
}

