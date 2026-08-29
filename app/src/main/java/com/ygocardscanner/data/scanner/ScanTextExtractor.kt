package com.ygocardscanner.data.scanner

import com.ygocardscanner.data.util.CatalogNormalizers

/** Extracts likely printed identifiers and title lines from ML Kit's plain-text output. */
object ScanTextExtractor {
    private val passcodePattern = Regex("\\b\\d{8}\\b")
    private val setCodePattern = Regex("\\b[A-Za-z]{2,6}(?:[ -]?[A-Za-z]{1,3})?[ -]?\\d{2,4}\\b")

    fun extract(rawText: String): ScanTextObservation {
        val lines = rawText.lines().map(String::trim).filter(String::isNotBlank)
        val setCodes = setCodePattern.findAll(rawText)
            .mapNotNull { CatalogNormalizers.setCode(it.value) }
            .distinct()
            .toList()
        val passcodes = passcodePattern.findAll(rawText).map { it.value }.distinct().toList()
        val names = lines.asSequence()
            .filter { line ->
                line.length in 3..80 && line.any(Char::isLetter) &&
                    passcodePattern.find(line) == null && setCodePattern.find(line) == null
            }
            .map { it.replace(Regex("\\s+"), " ") }
            .distinct()
            .take(MAX_NAME_CANDIDATES)
            .toList()
        return ScanTextObservation(rawText, setCodes, passcodes, names)
    }

    private const val MAX_NAME_CANDIDATES = 8
}
