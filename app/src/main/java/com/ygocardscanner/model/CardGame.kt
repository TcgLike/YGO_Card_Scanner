package com.ygocardscanner.model

enum class CardGame(val code: String) {
    YUGIOH("yugioh"),
    POKEMON("pokemon"),
    ;

    companion object {
        fun fromCode(value: String?): CardGame = entries.firstOrNull { it.code == value } ?: YUGIOH
    }
}
