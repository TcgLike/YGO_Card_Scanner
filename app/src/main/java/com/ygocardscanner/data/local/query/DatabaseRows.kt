package com.ygocardscanner.data.local.query

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.ygocardscanner.data.local.entity.InventoryEntry
import com.ygocardscanner.data.local.entity.Printing

data class CatalogPrintingRow(
    @Embedded
    val printing: Printing,
    @ColumnInfo(name = "display_name")
    val displayName: String,
)


data class ScannerPrintingRow(
    @Embedded
    val printing: Printing,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "matched_name")
    val matchedName: String,
)

data class CollectionEntryRow(
    @Embedded
    val entry: InventoryEntry,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "canonical_name")
    val canonicalName: String,
    @ColumnInfo(name = "passcode")
    val passcode: String?,
    @ColumnInfo(name = "catalog_set_name")
    val catalogSetName: String?,
    @ColumnInfo(name = "artwork_remote_url")
    val artworkRemoteUrl: String?,
    @ColumnInfo(name = "artwork_local_file_name")
    val artworkLocalFileName: String?,
    @ColumnInfo(name = "artwork_download_state")
    val artworkDownloadState: String?,
    @ColumnInfo(name = "artwork_message")
    val artworkMessage: String?,
)

data class InventoryEntryDetailRow(
    @Embedded
    val entry: InventoryEntry,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "canonical_name")
    val canonicalName: String,
    @ColumnInfo(name = "passcode")
    val passcode: String?,
    @ColumnInfo(name = "catalog_set_name")
    val catalogSetName: String?,
    @ColumnInfo(name = "artwork_remote_url")
    val artworkRemoteUrl: String?,
    @ColumnInfo(name = "artwork_local_file_name")
    val artworkLocalFileName: String?,
    @ColumnInfo(name = "artwork_download_state")
    val artworkDownloadState: String?,
    @ColumnInfo(name = "artwork_message")
    val artworkMessage: String?,
)