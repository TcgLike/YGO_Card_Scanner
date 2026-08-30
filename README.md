# YGO Card Scanner

YGO Card Scanner is a private, offline-first Android app for inventorying physical Yu-Gi-Oh! cards in English and German.

> **Project status:** the Inventory MVP and public card-catalog download are implemented. The repository is private for now and no license has been selected. A `LICENSE` file is intentionally not included; do not assume permission to reuse or redistribute the source until the project owner chooses a license for a public release.

This project is independent and is not affiliated with or endorsed by Konami or any Yu-Gi-Oh! rights holder.

## What works

- An app-private Room database holds collection data, catalog records, and catalog-update state.
- The add screen can download the public catalog, search it locally by English or German name, passcode, or set code, and display results in English or Deutsch.
- Users can add a known printing, edit quantity/condition/notes, remove an entry, or add an unknown printing manually.
- The optional camera scanner uses CameraX plus bundled offline Latin OCR to read card titles, set codes, and passcodes. It matches only the local Room catalog: exact set code, then passcode, then fuzzy English/German name. Camera frames and recognized text are never saved.
- Live scan remains open after each reviewed card is added. The same card must leave the frame before OCR re-arms, preventing accidental duplicate additions; Bulk photo still identifies multiple cards in one photographed image and presents them as a confirmation queue.
- The confirmation step shows the selected card's locally cached English artwork. A user can also opt in to download one primary English artwork per catalog card; files stay in app-private storage and the UI never hotlinks image URLs.
- The collection list and detail screens read only from Room. The Collection toolbar offers a persisted local view switcher: detailed cards, compact text-only rows, or artwork-only tiles.
- The Collection toolbar also opens an all-cards catalog browser backed only by active Room catalog records. It has the same three layouts and a check mark when any positive-quantity local inventory entry owns that canonical card; it never downloads artwork or catalog data while browsing.
- The update worker uses constrained, unique WorkManager work and never performs a network request from a Compose screen or ViewModel.
- Catalog replacement is atomic and inventory-safe: catalog rows that disappear are retained as inactive records, while collection rows and their snapshots are never deleted or overwritten.

The runtime architecture remains:

```text
Compose UI -> ViewModel -> Repository -> Room
```

Network responses are transport-only models. They are validated and mapped to Room before the UI can observe them.

Pokémon is implemented as an **English-only MVP** using the public Pokémon TCG API v2. It has its own app-private Room database, artwork directory, and WorkManager names; switching games cannot read, modify, or replace Yu-Gi-Oh! records. German Pokémon catalog data, variants/finishes, and camera scanning remain later milestones. The source assumptions are recorded in [docs/pokemon-source-spike.md](docs/pokemon-source-spike.md).

## Catalog source layout

Catalog code is deliberately separated by game:

```text
app/src/main/java/com/ygocardscanner/data/catalog/
├── universal/  # shared source contract, network DTOs, and Room mapper
├── pokemon/    # Pokémon TCG API client, DTOs, and English-only mapper
└── yugioh/     # YGOPRODeck, YGOJSON backup, and development catalog sources
```

Only `universal` code is allowed to be shared by both games. Game-specific sources depend on the universal contract but never on each other.

## Public catalog

The app uses public sources behind a shared `CatalogSource` contract:

- **Yu-Gi-Oh!**: [YGOPRODeck API v7](https://ygoprodeck.com/api-guide/), including its existing English/German catalog behavior.
- **Pokémon English-only MVP**: Pokémon TCG API v2 (`https://api.pokemontcg.io/v2/cards`), fetched in pages of at most 250 records. The public unauthenticated request limit is respected by spacing pages; a full first download currently takes several minutes. German Pokémon names/codes are intentionally not inferred or fabricated.

- Select **Download catalog** on the Add to collection screen for the first installation, or **Check for updates** later. The app does not silently download the catalog at startup.
- A lightweight provider revision is checked first. If it is unchanged, the full catalog is not downloaded again.
- The full update fetches paginated English and German card responses, merges records by the stable numeric provider ID/passcode, and stores the results locally. The page size is 1,000 and requests are throttled below the provider's stated limit.
- English responses also provide the first canonical artwork URL. It is catalog metadata only until downloaded into app-private storage. The optional full-image pack is processed as resumable batches; if it is interrupted, choose **Resume image download** to continue from its saved progress. It needs 3.5 GiB free device storage before starting, and has a hard 4 GiB cache ceiling. It downloads only one primary English artwork per card; catalog updates retain all inventory and invalidate changed artwork URLs without deleting inventory.
- English responses provide the canonical name and available printing/set-code rows. German responses contribute localized card names and text only.
- No image URL is exposed to the UI, and only a user-viewed card's English artwork is downloaded to local app-private storage. Price fields, account data, and analytics data are not requested or stored.

## Local price references

A normal user-requested **Download / refresh English + German catalog** also refreshes locally cached public price references. No price lookup runs from the UI and no card, collection, camera image, or personal data is sent to a pricing service.

- English catalog printings show YGOPRODeck's public `set_price` in USD when supplied for that exact set code.
- Card detail also lists public card-level vendor references: Cardmarket in EUR and TCGplayer, eBay, Amazon, and CoolStuffInc in USD when supplied. The provider defines these vendor values as the lowest price across versions of the card.
- Price records are stored as app-private Room `PriceSnapshot` rows with the time of the successful catalog mapping. They are estimates, not purchase offers or collection valuations, and they do not account for card condition, language, edition, grading, or a particular listing unless specifically marked for the set code.
- German printing-backup rows have no invented set price. Their catalog-result fallback, if present, is explicitly labelled as a card-level Cardmarket reference.

Prices are optional catalog metadata. Refreshing catalog/price data upserts those snapshots but never deletes or overwrites inventory entries.

### Important language and printing assumption

YGOPRODeck provides current English catalog data and the German localizations it has available. Its German `card_sets` responses repeat English-style set codes and do not provide verified German physical set codes or edition data. The app therefore does **not** invent German printing records: imported catalog printings are English/provider printings with an explicit `unknown` edition. German localized search and display work where the provider supplies a German text; English is the fallback otherwise.

Use **Add an unknown printing manually** for a German physical printing or edition that is not represented by the public source. This is intentional and preserves accurate inventory data over guessed metadata.

The old development seed remains only as a test fixture. After a successful public import it is deactivated, not deleted, so an inventory entry created against it remains intact.

Before any public release, re-check the provider's availability, data quality, attribution requirements, and terms of use.

## Optional German printing backup source

The default YGOPRODeck catalog remains the primary source. Users may opt in under **Settings** to the separate **German printing backup** source when they need verified German physical set codes such as `HSRD-DE006`.

- The optional source is YGOJSON's public `aggregate.zip` release. It is about 35 MB compressed at the time this was implemented and is downloaded only after the user explicitly enables it and selects its update action.
- It reads only passcodes plus German set prefixes, printing suffixes, rarity, and edition metadata. It does not import community card images, prices, or replace the primary card/text catalog.
- Its rows are joined only to active primary-catalog cards by passcode. A malformed download or a source card that cannot be matched is rejected/skipped; inventory is never modified.

- Disabling the setting removes this source from new local search and scanner candidates. Its retained catalog rows and any collection entries that use them are not deleted.

- YGOJSON is an independent community dataset assembled from YGOPRODeck, YAML Yugi, and Yugipedia. The project is MIT-licensed, while upstream/community content can carry separate terms and attribution requirements. Re-check its current release, data quality, and licensing before a public release: <https://github.com/iconmaster5326/YGOJSON>.

## Live-scan feedback

After a confirmed Live scan, the camera stays open and waits for the previous card to leave view before re-arming. Settings includes **Live scan feedback**, which controls a short, non-blocking card-to-deck acknowledgement animation. It is enabled by default, lasts about 1.25 seconds, and uses only the card artwork already cached in app-private storage; otherwise it shows a local text placeholder. It never downloads an image or sends camera content anywhere.

## Settings, language, and downloads

Open **Settings** from the Collection screen to choose English or Deutsch for the app UI, refresh the catalog, or start/resume the optional offline artwork pack. Tap the game name in the Collection toolbar to switch between the isolated Yu-Gi-Oh! and Pokémon workspaces. The language choice is stored only in the app-private preference store and immediately changes the catalog-search language used by Room.

**Download / refresh English + German catalog** always schedules a forced provider refresh. It downloads both provider languages and is the repair action for installations whose catalog was created before German localized card text was available. A refresh never deletes or overwrites collection entries.

The artwork control is resumable WorkManager work. It can continue while the app is not open, subject to Android's normal background-work, battery, storage, and network constraints. Selecting it again safely starts or resumes the saved download state.

## Yu-Gi-Oh! deck import

In **Yu-Gi-Oh!** mode, choose **Add** then **Import a deck** to import a local `.ydk` file or paste a `ydke://` code. Both formats contain card passcodes and are parsed entirely on-device; the importer does not upload the file or make a network request. `.ydk` is the common EDOPro/YGOPro/DuelingBook deck format, while YDKe is the shareable simulator-code form.

The review screen preserves Main, Extra, and Side quantities, combines them into the physical-card total, and resolves each passcode against the already-downloaded local catalog. A deck file does not identify a physical printing, language, rarity, edition, or condition. **Unknown printing** is therefore the safe default; users may choose a known local printing per card instead. Unrecognized passcodes block confirmation, and the approved batch is committed atomically so a failed import cannot leave a partial deck in the collection. Repeating an identical import increments its matching inventory quantities.

For sealed products with a shared printing prefix, enter an optional **deck printing base code** before review, such as `CH02-DE` or `CH02-DEXXX`. The importer normalizes the prefix, puts matching local printings first, orders those cards by the numeric suffix, and preselects a printing only when exactly one local match exists. It also lists resolved cards with no matching local printing so nothing is guessed. Deck files still contain only passcodes, not printed set codes; a base-code match is possible only after the relevant printings are present in the local catalog. For German print codes, enable and update the optional German printing backup in Settings before reviewing the deck.

**Can I build it?** is also available from **Add** in Yu-Gi-Oh! mode. It accepts the same local .ydk and ydke:// inputs, reads no network data, and compares each required passcode quantity with all owned copies in the app-private collection. Rows are green when enough copies are owned; insufficient or unavailable catalog cards keep the normal color. This is a read-only check and never changes inventory.

Custom-card XML, name-only deck lists, remote deck URLs, and Pokémon deck import are intentionally out of scope for this milestone.
## Privacy and data handling

- Personal collection data remains in the app-private Room database on the device.
- There is no login, account, cloud sync, Firebase, analytics, telemetry, advertising, or subscription functionality.
- Android's Internet permission is used only by the user-requested public catalog update worker.
- Price data and cloud features are not implemented in this milestone. Camera scanning is fully local: the bundled OCR model, temporary camera frames, and matching do not send card images or text to a service. Artwork is intentionally limited to one locally cached English canonical image per viewed catalog card; it is not proof of a particular physical printing or edition.
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
4. Open **Settings**, select **Download / refresh English + German catalog**, wait for the status to report completion, then search locally.
5. Optionally choose **Download offline card images** in Settings to cache one English image per catalog card. Select a search result to preview its cached image while adding; all cached images remain available offline.
6. Choose **Scan a card** from Add to collection and grant camera permission. Keep one card in view; review the local match before adding it. Use **Bulk** for continuous one-card-at-a-time scanning.

## Verification and project hygiene

The test suite covers catalog mapping and parsing, paginated English/German merge behavior, English-only artwork selection, provider-version parsing, Room migrations, durable update state, inventory persistence across database recreation, catalog replacement safety, artwork cache invalidation, and worker success/retry/failure states.

See [CONTRIBUTING.md](CONTRIBUTING.md) for project conventions and [SECURITY.md](SECURITY.md) for the private security-reporting process. GitHub issue, contribution, and conduct templates are present so the repository can be opened to contributors when the owner chooses a license and public-release policy.
