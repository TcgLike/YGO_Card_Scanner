# Security Policy

## Supported versions

YGO Card Scanner is pre-release software and does not yet have a supported public release line. Security fixes are evaluated on a best-effort basis while the project remains private.

## Reporting a vulnerability

Please do **not** open a public issue for a suspected vulnerability.

While this repository is private, report it directly to the repository maintainer through the private channel used to grant repository access. Include a concise description, affected revision/build, reproduction steps, and potential impact. The maintainer will acknowledge the report, assess it, and coordinate a fix when appropriate.

Do not include any of the following in a report unless the maintainer explicitly asks for it through a secure channel:

- A real collection database, JSON backup, CSV export, screenshot containing collection data, or device identifier.
- API keys, signing keys, tokens, or `local.properties`.
- Exploit attempts against third-party services or catalog providers.

Once the project is public, this policy should be updated with a dedicated private vulnerability-reporting route before public issue reporting is enabled.

## Security scope

Relevant reports include issues involving:

- Access to or unintended disclosure of locally stored collection data.
- Unsafe import/export parsing when those features are added.
- Room migration or catalog-update behavior that could corrupt or erase inventory.
- Dependency, build, permission, or future catalog-update security issues.

The Inventory MVP intentionally has no account system, cloud service, analytics, advertising, scanner, or pricing. Its only production network features are the user-requested public catalog update and the on-demand English artwork cache, both of which are relevant to reports involving provider data validation, transport behavior, downloaded-file validation, or catalog-to-Room mapping. Camera scanning is not implemented yet.

## Data handling note

The app's collection database is intended to be app-private. User-created backups and exports, once implemented, are files chosen by the user and must be treated as sensitive personal collection data. They are excluded from version control by the repository's `.gitignore`.

