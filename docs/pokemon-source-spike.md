# Pokémon source spike

Status: **implemented for the English-only MVP**.

## Candidate source

TCGdex is the current candidate for a Pokémon catalog because its documentation describes
language-specific REST endpoints, including English and German, stable-looking card IDs, set/card
identifiers, and artwork URLs. Its catalog is separate from the existing Yu-Gi-Oh! sources.

The Pokémon TCG API v2 remains an English-focused fallback only. Its documentation supports
paginated card retrieval, images, and price fields, but this project must not assume it provides
the German catalog needed by the product.

## What was verified

### Pokémon TCG API v2 English fixture

A user-provided live `GET https://api.pokemontcg.io/v2/cards` response was inspected. It confirms:

- `totalCount` was 20,479 and `pageSize` was 250, so a complete import is currently about 82 pages.
- A card has a stable-looking provider ID such as `hgss4-1`, plus `set.id` and a physical `number`.
  These three fields are enough to form a source-scoped physical-card identity.
- Records include English card name/text, set metadata, rarity, small and large image URLs, and
  Cardmarket/TCGplayer price objects.
- Price buckets such as `holofoil` and `reverseHolofoil` demonstrate that Pokémon finish/variant is
  distinct inventory metadata. It must not be silently stored as Yu-Gi-Oh! edition.
- `cardmarket.prices.germanProLow` is a market-price field; it does **not** establish that German
  names, text, or German physical card identifiers are present.

### TCGdex documentation

- TCGdex documents English and German as supported languages and documents card and set endpoints.
- Its documented card-list response includes an ID, local card ID, localized name, and image URL.

## Source direction

Use Pokémon TCG API v2 as the primary English physical-card catalog candidate. Use TCGdex only as a
separate, opt-in-or-fallback German localization source if the remaining cross-language identity
checks pass. The two sources must remain mapped in an isolated Pokémon workspace and must never
modify Yu-Gi-Oh! records.

## TCGdex follow-up blocker

Live TCGdex sample requests could not be verified from the current development environment: the
in-app browser rejected or timed out the API URLs and the direct read-only probe returned no usable
payload. Therefore TCGdex is not used by the app. The English-only MVP uses Pokémon TCG API v2; its parser, workspace, and game-mode UI were added without depending on TCGdex.

Before expanding the English-only MVP, run the following read-only checks from a normal development
network and save anonymized fixture responses:

1. Fetch the same known card from TCGdex `/v2/en/cards/{id}` and `/v2/de/cards/{id}`. Confirm that the
   ID and physical set/card identifier remain stable while the localized name/text changes, then prove how
   that ID maps to the Pokémon TCG API v2 `set.id` + `number` identity.
2. Fetch at least one current set and one older set in both languages. Record coverage gaps,
   variants, rarity, set name, local card number, and image fields.
3. Confirm an update strategy: list/set revisions, cache headers, or another safe way to avoid a
   full download when data is unchanged.
4. Review the source and image terms, attribution requirements, and permission to cache images in
   an offline app before enabling any artwork download.
5. Measure a representative image sample before setting a Pokémon-specific cache quota.

## Isolation rule applied by the MVP

Pokémon uses its own app-private Room database, artwork directory, WorkManager unique-work names, and catalog metadata. Yu-Gi-Oh! database files, migrations, repositories, artwork cache, scanner matching, and catalog jobs remain untouched. The game-mode switch selects one workspace at a time.
