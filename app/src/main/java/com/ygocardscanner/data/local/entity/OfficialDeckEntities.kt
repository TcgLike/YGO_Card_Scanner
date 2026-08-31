package com.ygocardscanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "official_deck_catalog_state")
data class OfficialDeckCatalogState(
    @PrimaryKey @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "catalog_revision") val catalogRevision: String,
    @ColumnInfo(name = "installed_at_epoch_millis") val installedAtEpochMillis: Long,
)

@Entity(tableName = "official_deck_products", indices = [Index(value = ["product_type"]), Index(value = ["release_date"])])
data class OfficialDeckProduct(
    @PrimaryKey @ColumnInfo(name = "product_id") val productId: String,
    val title: String,
    @ColumnInfo(name = "product_type") val productType: String,
    @ColumnInfo(name = "release_date") val releaseDate: String,
    @ColumnInfo(name = "official_product_url") val officialProductUrl: String,
    @ColumnInfo(name = "cover_style") val coverStyle: String,
    @ColumnInfo(name = "source_note") val sourceNote: String,
)

@Entity(
    tableName = "official_deck_variants",
    foreignKeys = [ForeignKey(entity = OfficialDeckProduct::class, parentColumns = ["product_id"], childColumns = ["product_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["product_id"])],
)
data class OfficialDeckVariant(
    @PrimaryKey @ColumnInfo(name = "variant_id") val variantId: String,
    @ColumnInfo(name = "product_id") val productId: String,
    val title: String,
    @ColumnInfo(name = "total_card_count") val totalCardCount: Int,
    @ColumnInfo(name = "is_complete_box_contents") val isCompleteBoxContents: Boolean,
)

@Entity(
    tableName = "official_deck_cards",
    primaryKeys = ["variant_id", "passcode", "section_code", "option_group_id"],
    foreignKeys = [ForeignKey(entity = OfficialDeckVariant::class, parentColumns = ["variant_id"], childColumns = ["variant_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["variant_id"]), Index(value = ["passcode"])],
)
data class OfficialDeckCard(
    @ColumnInfo(name = "variant_id") val variantId: String,
    val passcode: String,
    @ColumnInfo(name = "section_code") val sectionCode: String,
    val quantity: Int,
    @ColumnInfo(name = "option_group_id") val optionGroupId: String = "",
)

