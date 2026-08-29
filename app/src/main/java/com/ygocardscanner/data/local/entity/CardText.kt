package com.ygocardscanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "card_texts",
    primaryKeys = ["card_id", "language_code"],
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["card_id"],
            childColumns = ["card_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["card_id"]),
        Index(value = ["normalized_name"]),
    ],
)
data class CardText(
    @ColumnInfo(name = "card_id")
    val cardId: String,
    @ColumnInfo(name = "language_code")
    val languageCode: String,
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    val description: String?,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,
    @ColumnInfo(name = "catalog_revision")
    val catalogRevision: String,
)
