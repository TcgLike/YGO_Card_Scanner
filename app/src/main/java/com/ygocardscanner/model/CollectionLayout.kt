package com.ygocardscanner.model

/** The app-private display preference for the local collection. */
enum class CollectionLayout(val code: String) {
    DETAILED("detailed"),
    COMPACT("compact"),
    ARTWORK_TILES("artwork_tiles"),
    ;

    companion object {
        fun fromCode(value: String?): CollectionLayout =
            entries.firstOrNull { it.code == value } ?: DETAILED
    }
}

