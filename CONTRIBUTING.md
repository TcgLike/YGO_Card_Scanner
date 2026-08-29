# Contributing

Thank you for helping improve YGO Card Scanner. The repository is private during its early development; contributions may be invited by the maintainer before a public release. This guide is written so it remains useful once the repository is opened.

## Ground rules

- Keep changes scoped to one agreed milestone. Do not add future features incidentally.
- Preserve the offline-first, privacy-first design: no accounts, cloud sync, Firebase, analytics, telemetry, advertising, subscriptions, or image hotlinking.
- Do not include real collection data, database files, backups, exports, API keys, signing keys, or personal information in a commit, issue, test, or screenshot.
- Do not add or change a project license without an explicit maintainer decision.
- Report suspected vulnerabilities privately as described in [SECURITY.md](SECURITY.md).

## Local setup and verification

Use JDK 17 and install Android SDK Platform 35. The app supports Android API 26 and later. Use the checked-in Gradle wrapper rather than a system Gradle installation.

On Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
.\gradlew.bat testDebugUnitTest
```

On macOS or Linux:

```bash
./gradlew assembleDebug
./gradlew lintDebug
./gradlew testDebugUnitTest
```

Run the applicable commands before proposing a change. For UI or Room instrumentation coverage, also run the relevant `connectedDebugAndroidTest` tests on an emulator or device when available.

## Architecture and data rules

- Keep the main flow as Compose UI -> ViewModel -> repository -> Room.
- The UI reads collection data only from Room-backed repositories, never directly from a network response.
- Keep canonical card, localized card text, printing, inventory entry, catalog metadata, and future price snapshot data separate.
- Put external catalog access behind `CatalogSource`; map network models to Room models before persistence.
- Catalog updates must not delete or overwrite inventory, including unknown-printing entries.
- Use stable provider IDs and passcodes where possible, and normalize set codes consistently.
- Do not use destructive Room migrations. Include the migration and migration tests whenever a released schema changes, and preserve/export Room schemas as configured by the project.

## Tests and documentation

Add or update focused tests for behavior that changes. At a minimum, database work should cover mapping/parsing, quantity updates, catalog replacement safety, and migrations where applicable. Keep fixtures small, synthetic, and English/German where localization is relevant.

Update `README.md` when a decision affects privacy, data sources, build/run instructions, catalog assumptions, or milestone scope. If an external catalog/API behavior is uncertain, add a small test or research spike instead of guessing how it behaves.

## Pull requests

Use the pull-request template and describe:

- The milestone and user-visible behavior affected.
- Tests and verification run.
- Any Room schema or migration impact.
- Any change to device data, permissions, or network access.

By participating, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

