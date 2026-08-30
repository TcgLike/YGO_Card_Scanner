# Technical details

This document is for contributors and anyone interested in how YGO Card Scanner is built. For a user-focused overview, see the [README](../README.md).

## Platform and architecture

YGO Card Scanner is a native Android application written in Kotlin. It uses Jetpack Compose for the interface and follows a simple one-way structure:

```text
Compose UI -> ViewModel -> Repository -> Room database
```

The app targets Android 8.0 (API 26) and newer, compiles against Android SDK 35, and uses JDK 17. The main Android libraries are:

- **Room** for the app-private SQLite database.
- **CameraX** for camera access.
- **Bundled ML Kit Latin text recognition** for on-device OCR.
- **WorkManager** for reliable catalog and artwork downloads.
- **Kotlin serialization** for catalog parsing.

The relevant source code is grouped under `app/src/main/java/com/ygocardscanner/`:

```text
data/          local storage, catalog sources, scanning, workers, and repositories
model/         application models
ui/            Compose screens, view models, navigation, and localization
di/            application container and workspace wiring
```

## Local data model

The collection, catalog, scan matches, settings, update state, and price snapshots are stored in Room inside the app's private storage. Optional downloaded artwork is also held in app-private storage. Android backup is disabled.

There is no account, cloud database, analytics SDK, advertising SDK, or remote collection service. The app does not transmit collection entries, notes, camera images, or recognized text. Clearing the app's storage removes the local database and its cached files.

## Catalog and content downloads

Network access is limited to user-requested catalog or artwork updates, which run through constrained, unique WorkManager jobs. Screens and view models do not make network calls directly.

The primary Yu-Gi-Oh! catalog source is the public [YGOPRODeck API v7](https://ygoprodeck.com/api-guide/). The update process downloads English and German data, validates it, merges it using stable provider identifiers and passcodes, and maps it into Room before the UI can access it. A lightweight provider revision check avoids an unnecessary full download when possible.

The public catalog does not always provide verified German physical printing codes and editions. The optional **German printing backup** uses YGOJSON's public `aggregate.zip` release to enrich local search and scanning with German printing metadata. It is disabled by default and must be enabled and updated explicitly. Its rows are joined to active primary-catalog cards by passcode; malformed or unmatched data is ignored and inventory is never changed.

Catalog replacement is inventory-safe. Entries which disappear from a refreshed source become inactive catalog records; the user's collection rows and their saved snapshots are not removed or overwritten.

Public price references are cached as local metadata when the catalog is refreshed. They are provider-supplied reference values, not purchase offers or collection valuations, and do not account for factors such as condition, language, edition, grading, or an individual listing.

### Artwork cache

Artwork URLs are catalog metadata only. An image is downloaded only when the user chooses to view or cache it, then stored locally; the UI never hotlinks provider image URLs. The optional full offline image pack stores one primary English image per catalog card, supports resuming, requires 3.5 GiB free storage before it begins, and has a 4 GiB cache limit.

## Scan and matching flow

Live scan uses CameraX with bundled on-device Latin OCR. The app extracts card titles, set codes, and passcodes from temporary frames, then searches the already-downloaded local catalog in this order:

1. Exact set code
2. Passcode
3. Fuzzy English or German card name

Frames and recognized text are not saved. The user reviews a candidate before it is added. To avoid accidental duplicate additions, a confirmed card must leave the live camera frame before recognition is armed again. The bulk-photo flow builds a local confirmation queue for multiple cards in a photograph.

## Deck import and availability

The Yu-Gi-Oh! deck features accept local `.ydk` files and pasted `ydke://` codes. Both are parsed on-device, producing Main, Extra, and Side quantities which are resolved against the downloaded catalog by passcode. The import requires review and commits the approved batch atomically.

Because deck formats do not identify a physical printing, language, rarity, edition, or condition, an unknown printing is the safe default. The optional printing-base-code helper can prioritize matching local printings but never guesses one. The **Can I build it?** check compares deck quantities with the local collection without changing inventory.

## Language support

The interface supports English and German. Visible Compose text is selected through the `appText(...)` and `UiTextToken` localization layer. The `scripts/verify_ui_text_tokens.py` check prevents new direct visible string literals outside that layer.

## Build and test

Use the included Gradle wrapper from the repository root. Android Studio should use JDK 17 and Android SDK Platform 35.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

On macOS or Linux, use the corresponding `./gradlew` commands. The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

The verification suite covers catalog parsing and merging, Room migrations, update state, inventory persistence, catalog replacement safety, artwork cache invalidation, and worker success, retry, and failure behaviour.

## Data-source and trademark notices

YGO Card Scanner is independent and is not affiliated with or endorsed by Konami or any Yu-Gi-Oh! rights holder. Yu-Gi-Oh! content and external data sources may be subject to their own terms, attribution requirements, and intellectual-property rights.

The optional German-printing source is [YGOJSON](https://github.com/iconmaster5326/YGOJSON), an MIT-licensed community dataset assembled from YGOPRODeck, YAML Yugi, and Yugipedia. Re-check the current availability, data quality, attribution requirements, and upstream terms before redistributing or relying on external catalog content.
