package com.ygocardscanner.data.deckimport.yugioh

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/** Parsers for the passcode-based formats commonly exported by Yu-Gi-Oh! simulators. */
object YgoDeckParsers {
    private val passcodePattern = Regex("\\d{1,8}")

    fun parse(sourceLabel: String, rawInput: String): YgoDeckDocument {
        val trimmed = rawInput.trim()
        require(trimmed.isNotEmpty()) { "Choose a .ydk file or paste a YDKe code first." }
        return if (trimmed.startsWith(YDKE_PREFIX, ignoreCase = true)) {
            parseYdke(sourceLabel, trimmed)
        } else {
            parseYdk(sourceLabel, trimmed)
        }
    }

    fun parseYdk(sourceLabel: String, rawInput: String): YgoDeckDocument {
        var section: YgoDeckSection? = null
        val cards = mutableMapOf(
            YgoDeckSection.MAIN to mutableListOf<String>(),
            YgoDeckSection.EXTRA to mutableListOf<String>(),
            YgoDeckSection.SIDE to mutableListOf<String>(),
        )
        rawInput.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            when (line.lowercase()) {
                "#main" -> section = YgoDeckSection.MAIN
                "#extra" -> section = YgoDeckSection.EXTRA
                "!side" -> section = YgoDeckSection.SIDE
                "" -> Unit
                else -> {
                    if (line.startsWith("#")) return@forEachIndexed
                    val activeSection = requireNotNull(section) {
                        "Line ${index + 1}: card passcodes must appear under #main, #extra, or !side."
                    }
                    cards.getValue(activeSection) += normalizePasscode(line, index + 1)
                }
            }
        }
        require(cards.values.any(List<String>::isNotEmpty)) { "The .ydk file does not contain any card passcodes." }
        return YgoDeckDocument(sourceLabel, cards)
    }

    fun parseYdke(sourceLabel: String, rawInput: String): YgoDeckDocument {
        val body = rawInput.trim().removePrefixIgnoreCase(YDKE_PREFIX)
        val components = body.split('!')
        require(components.size >= 3) { "YDKe must contain Main, Extra, and Side components." }
        return YgoDeckDocument(
            sourceLabel = sourceLabel,
            cardsBySection = mapOf(
                YgoDeckSection.MAIN to decodeYdkeComponent(components[0], "Main"),
                YgoDeckSection.EXTRA to decodeYdkeComponent(components[1], "Extra"),
                YgoDeckSection.SIDE to decodeYdkeComponent(components[2], "Side"),
            ),
        ).also { require(it.totalCardCount > 0) { "The YDKe code does not contain any card passcodes." } }
    }

    private fun decodeYdkeComponent(component: String, label: String): List<String> {
        if (component.isBlank()) return emptyList()
        val bytes = try {
            Base64.getDecoder().decode(component)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("$label YDKe component is not valid Base64.")
        }
        require(bytes.size % Int.SIZE_BYTES == 0) { "$label YDKe component has an invalid passcode length." }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).let { buffer ->
            buildList {
                while (buffer.remaining() >= Int.SIZE_BYTES) {
                    val value = buffer.int.toLong() and 0xffffffffL
                    require(value in 1..MAX_PASSCODE) { "$label YDKe component contains an invalid passcode." }
                    add(value.toString().padStart(PASSCODE_LENGTH, '0'))
                }
            }
        }
    }

    private fun normalizePasscode(value: String, lineNumber: Int): String {
        require(passcodePattern.matches(value)) { "Line $lineNumber: '$value' is not an 8-digit Yu-Gi-Oh! passcode." }
        val numeric = value.toLong()
        require(numeric in 1..MAX_PASSCODE) { "Line $lineNumber: '$value' is not a valid Yu-Gi-Oh! passcode." }
        return numeric.toString().padStart(PASSCODE_LENGTH, '0')
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

    private const val YDKE_PREFIX = "ydke://"
    private const val PASSCODE_LENGTH = 8
    private const val MAX_PASSCODE = 99_999_999L
}
