package com.ygocardscanner.ui.navigation

import android.net.Uri

object Destinations {
    const val COLLECTION = "collection"
    const val ADD = "add"
    const val MANUAL = "manual"
    const val SCANNER = "scanner"
    const val SETTINGS = "settings"
    const val DETAIL_PATTERN = "detail/{entryId}"

    fun detail(entryId: String): String = "detail/${Uri.encode(entryId)}"
}
