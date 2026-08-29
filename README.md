# YGO Card Scanner

YGO Card Scanner is a private, offline-first Android app for inventorying physical Yu-Gi-Oh! cards in English and German.

> **Project status:** the Inventory MVP and public card-catalog download are implemented. The repository is private for now and no license has been selected. A `LICENSE` file is intentionally not included; do not assume permission to reuse or redistribute the source until the project owner chooses a license for a public release.

This project is independent and is not affiliated with or endorsed by Konami or any Yu-Gi-Oh! rights holder.

## What works

- An app-private Room database holds collection data, catalog records, and catalog-update state.
- The add screen can download the public catalog, search it locally by English or German name, passcode, or set code, and display results in English or Deutsch.
- Users can add a known printing, edit quantity/condition/notes, remove an entry, or add an unknown printing manually.
- Card detail downloads and displays one English canonical artwork only after a user views that card. The image is saved in app-private storage; the UI never hotlinks an image URL.
- The collection list and detail screens read only from Room.
- The update worker uses constrained, unique WorkManager work and never performs a network request from a Compose screen or ViewModel.
- Catalog replacement is atomic and inventory-safe: catalog rows that disappear are retained as inactive records, while collection rows and their snapshots are never deleted or overwritten.

The runtime architecture remains:

```text
Compose UI -> ViewModel -> Repository -> Room
```

Network responses are transport-only models. They are validated and mapped to Room before the UI can observe them.

## Public catalog

The app currently uses the documented public [YGOPRODeck API v7](https://ygoprodeck.com/api-guide/) behind `CatalogSource`.

- Select **Download catalog** on the Add to collection screen for the first installation, or **Check for updates** later. The app does not silently download the catalog at startup.
- A lightweight provider revision is checked first. If it is unchanged, the full catalog is not downloaded again.
- The full update fetches paginated English and German card responses, merges records by the stable numeric provider ID/passcode, and stores the results locally. The page size is 1,000 and requests are throttled below the provider's stated limit.
- English responses also provide the first canonical artwork URL. It is catalog metadata only until a user opens that card's detail. A constrained worker then downloads and validates the image into app-private storage; there is no image hotlinking and no automatic full-image bulk download.
- English responses provide the canonical name and available printing/set-code rows. German responses contribute localized card names and text only.
- No image URL is exposed to the UI, and only a user-viewed card's English artwork is downloaded to local app-private storage. Price fields, account data, and analytics data are not requested or stored.

### Important language and printing assumption

YGOPRODeck provides current English catalog data and the German localizations it has available. Its German `card_sets` responses repeat English-style set codes and do not provide verified German physical set codes or edition data. The app therefore does **not** invent German printing records: imported catalog printings are English/provider printings with an explicit `unknown` edition. German localized search and display work where the provider supplies a German text; English is the fallback otherwise.

Use **Add an unknown printing manually** for a German physical printing or edition that is not represented by the public source. This is intentional and preserves accurate inventory data over guessed metadata.

The old development seed remains only as a test fixture. After a successful public import it is deactivated, not deleted, so an inventory entry created against it remains intact.

Before any public release, re-check the provider's availability, data quality, attribution requirements, and terms of use.

## Privacy and data handling

- Personal collection data remains in the app-private Room database on the device.
- There is no login, account, cloud sync, Firebase, analytics, telemetry, advertising, or subscription functionality.
- Android's Internet permission is used only by the user-requested public catalog update worker.
- Price data, CameraX, bundled ML Kit OCR, and cloud features are not implemented in this milestone. Artwork is intentionally limited to one locally cached English canonical image per viewed catalog card; it is not proof of a particular physical printing or edition.
- Complete local JSON backup/import and CSV export remain future user-initiated local-file features, not cloud sync.

Clearing app storage removes its local database. Do not use real collection data in test fixtures, bug reports, or CI artifacts.

## Build and run

### Requirements

- Android Studio with Android SDK Platform 35 installed.
- JDK 17. The project configures Kotlin and Java for Java 17.
- An Android 8.0 (API 26) or newer emulator/device for instrumentation tests.

The Gradle wrapper is included, so a separate Gradle installation is not required. Android Studio creates `local.properties` for the local SDK; keep it untracked.

### Command line

From the repository root on Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

On macOS or Linux:

```bash
./gradlew assembleDebug
./gradlew lintDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Android Studio

1. Open the repository root and allow Gradle sync to finish.
2. Create/select an API 26+ emulator or connect a device.
3. Select the `app` configuration and press **Run**.
4. Open **Add to collection**, select **Download catalog**, wait for the status to report completion, then search locally.
5. Open a collection entry to queue its English card image. Once it finishes, the detail screen reads the cached local file even while offline.

## Verification and project hygiene

The test suite covers catalog mapping and parsing, paginated English/German merge behavior, English-only artwork selection, provider-version parsing, Room migrations, durable update state, inventory persistence across database recreation, catalog replacement safety, artwork cache invalidation, and worker success/retry/failure states.

See [CONTRIBUTING.md](CONTRIBUTING.md) for project conventions and [SECURITY.md](SECURITY.md) for the private security-reporting process. GitHub issue, contribution, and conduct templates are present so the repository can be opened to contributors when the owner chooses a license and public-release policy.
