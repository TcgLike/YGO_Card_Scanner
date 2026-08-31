package com.ygocardscanner.ui.localization

import com.ygocardscanner.model.PriceQuote
import java.math.BigDecimal
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Date

fun PriceQuote.formattedAmount(): String = try {
    NumberFormat.getCurrencyInstance().apply {
        currency = Currency.getInstance(currencyCode)
    }.format(BigDecimal.valueOf(amountMinor, 2))
} catch (_: IllegalArgumentException) {
    "$currencyCode ${BigDecimal.valueOf(amountMinor, 2)}"
}

fun PriceQuote.providerLabel(): String = when (providerId) {
    "set_price" -> "Set price"
    "cardmarket" -> "Cardmarket"
    "tcgplayer" -> "TCGplayer"
    "ebay" -> "eBay"
    "amazon" -> "Amazon"
    "coolstuffinc" -> "CoolStuffInc"
    else -> providerId
}

fun PriceQuote.formattedObservedAt(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(
        Date(observedAtEpochMillis),
    )

