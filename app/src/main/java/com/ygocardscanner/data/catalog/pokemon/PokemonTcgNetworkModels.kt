package com.ygocardscanner.data.catalog.pokemon
import kotlinx.serialization.Serializable

@Serializable
data class PokemonTcgCardPageDto(
    val data: List<PokemonTcgCardDto>,
    val page: Int,
    val pageSize: Int,
    val count: Int,
    val totalCount: Int,
)

@Serializable
data class PokemonTcgCardDto(
    val id: String,
    val name: String,
    val set: PokemonTcgSetDto,
    val number: String,
    val rarity: String? = null,
    val images: PokemonTcgImagesDto? = null,
)

@Serializable
data class PokemonTcgSetDto(
    val id: String,
    val name: String? = null,
)

@Serializable
data class PokemonTcgImagesDto(
    val small: String? = null,
    val large: String? = null,
)
