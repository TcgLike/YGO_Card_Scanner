# YGO Card Scanner

YGO Card Scanner is an offline-first Android app for organizing a physical Yu-Gi-Oh! card collection. Search for cards, scan them with your phone, record the copies you own, and check whether you can build a deck from your collection.

## What you can do

- **Build and maintain a card collection.** Add cards from the catalog, record quantities, condition, and notes, or add an unknown printing manually when the exact version is unavailable.
- **Find cards your way.** Search a downloaded catalog by English or German card name, passcode, or set code, then browse it in detailed, compact, or artwork-focused layouts.
- **Scan physical cards.** Use live camera scanning for one card at a time or review several cards from a bulk photo. Every proposed match is reviewed before it is added.
- **Work in English or German.** Switch the app interface and use local English/German catalog search and card information where available.
- **Keep a local image library.** Preview cached card artwork and optionally download an offline artwork pack for the catalog.
- **Import a deck list.** Import a local `.ydk` file or paste a `ydke://` deck code, review the cards, and add the required quantities to your collection.
- **Check deck availability.** Use **Can I build it?** to compare an imported deck list with the cards already in your collection without changing anything.

## Project status

This is a personal hobby project, built primarily for my own collection and learning. I am a software developer, but this is not a full-time product or a promise to support every possible use case, card source, or feature request. I am happy to share it freely and welcome constructive feedback, but development happens in my spare time and follows the needs of the project first.

## Your data stays on your device

Your collection, notes, scan results, settings, and downloaded images are kept in the app's private storage on your device. The app has no login, account, cloud sync, analytics, advertising, or subscription service.

Card recognition happens on your device. Camera frames and recognized text are not saved or sent to a service.

The app connects to public card-data providers only when you choose to download or refresh the card catalog, price references, or optional card artwork. Those requests do not upload your collection, notes, scanned images, or other personal data. Clearing the app's storage removes its local data, so keep that in mind before resetting or uninstalling it.

## Getting started

1. Install and open the app.
2. From **Settings** or **Add to collection**, choose **Download / refresh English + German catalog**.
3. Search for a card or choose **Scan a card**.
4. Review the match, choose the printing where known, and add it to your collection.

The catalog download is optional but needed before local search, card matching, and deck availability checks can identify cards.

## Important notes

- Card and price information comes from public community data sources and may be incomplete or change over time. Price references are estimates, not valuations or purchase offers.
- Some German physical printings cannot be verified by the primary public catalog. Use the optional German-printing download in Settings when it is useful, or add a printing manually instead of guessing.
- YGO Card Scanner is independent and is not affiliated with or endorsed by Konami or any Yu-Gi-Oh! rights holder.

## Further reading

- [Technical details](docs/technical-details.md) architecture, local storage, catalog sources, scan matching, builds, and tests.
- [Contributing guide](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
- [MIT License](LICENSE)

## Official deck library

The deck-import screen includes an **Official deck library**. It installs a versioned, curated offline list into the app-private database and then reuses the normal import review, so nothing reaches the collection until it is confirmed.

The bundled library contains 20 modern complete official Structure Deck lists (from Structure Deck: Fire Kings through Machine Reactor Structure Deck), plus the original Yugi Muto, Seto Kaiba, and Codebreaker decks. It also supports Structure Deck: Soulburner with its verified duplicate Volcanic Shell, and Blue-Eyes White Destiny plus both all-foil Chronicles products. Those three 51-card products import their fixed base and explicitly ask the user to choose the actual random bonus card (or safely skip it); the app never guesses a bonus. Yugi's Legendary Decks remains a placeholder until its three independent deck lists and quantities have been individually verified.

Product identity and total counts are checked against official Yu-Gi-Oh! product information. Passcodes are curated from the same public card-catalog provider already used by the app, then stored locally in `app/src/main/assets/official-decks/official-decks-v1.json`. The app does not scrape or query Konami's card database at runtime. The box visuals are generic local illustrations, not product packaging images.

## Build and verify

With JDK 17 available, run:

```powershell
.\gradlew.bat assembleDebug lintDebug testDebugUnitTest
```

The debug APK is written under `app/build/outputs/apk/debug/`.
