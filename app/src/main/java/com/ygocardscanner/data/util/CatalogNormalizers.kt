package com.ygocardscanner.data.util

import java.text.Normalizer
import java.util.Locale

object CatalogNormalizers {
    fun setCode(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { raw ->
            Normalizer.normalize(raw, Normalizer.Form.NFKD)
                .uppercase(Locale.ROOT)
                .filter { it.isLetterOrDigit() }
                .takeIf { it.isNotEmpty() }
        }

    fun name(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace("ß", "ss")
        .replace(Regex("\\p{M}"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}
