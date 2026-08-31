# Security Policy

## Supported versions

Security fixes are considered for the latest release published on GitHub. Earlier releases may no longer receive fixes, so update to the newest available version before reporting an issue where possible.

## Reporting a vulnerability

Please **do not** report suspected vulnerabilities in a public issue or discussion.

Use [GitHub Private Vulnerability Reporting](https://github.com/TcgLike/YGO_Card_Scanner/security/advisories/new) to submit a report privately. If the private-report form is unavailable, do not publish the details; contact the repository owner through an existing private channel instead.

Include:

- The affected app version or Git commit and where the APK was obtained.
- Device model and Android version.
- Clear, safe steps to reproduce the issue.
- The security impact, such as possible data exposure, inventory corruption, or unintended network access.
- Any proof of concept that is necessary to understand the issue, without accessing data that does not belong to you.

Do not include real collection databases, backups, exports, screenshots containing private collection data, device identifiers, API keys, signing keys, passwords, tokens, or `local.properties`. Do not test against third-party catalog providers in a way that could disrupt their service.

Reports are handled on a best-effort basis. Please allow time for assessment and remediation before public disclosure. We will coordinate disclosure with the reporter where practical.

## Security scope

Relevant reports include:

- Unintended access to, disclosure of, or loss of collection data, notes, downloaded artwork, or settings stored by the app.
- Camera, scanner, or OCR behaviour that saves or transmits images or recognised text unexpectedly.
- Unsafe parsing of local `.ydk` files or `ydke://` deck codes, including cases that could crash the app, corrupt inventory, or expose data.
- Catalog, artwork, or German-printing downloads that bypass expected validation, expose local data, or allow unsafe content to affect the app.
- Room migration, catalog update, or import behaviour that can delete, overwrite, or corrupt collection entries.
- Security problems in dependencies, release signing, build tooling, app permissions, or the APK distribution process.

The following are usually not security vulnerabilities:

- Incorrect or incomplete public catalog, printing, artwork, or price data without a security impact.
- Feature requests, general support questions, and ordinary application bugs. Use GitHub Discussions or the bug-report form for those.
- Issues requiring physical access to an unlocked device, a compromised operating system, or a rooted device, unless they show an app-specific weakness.

## Data handling

YGO Card Scanner is designed to keep collection data on the device in app-private storage. Android backup is disabled. The app has no account system, cloud sync, analytics, advertising, telemetry, or subscription service.

Card recognition is performed on-device. Camera frames and recognised text are not stored or sent to a service. Network access is limited to user-requested public catalog, price-reference, and optional artwork or German-printing updates; those requests do not upload collection entries, notes, scanned images, or other personal data.

App-private storage is not a protection against a compromised or rooted device. Clearing app storage or uninstalling the app removes its local database and cached files.

## Security updates

When a vulnerability is confirmed, the maintainer will assess its severity, prepare a fix or mitigation where feasible, and publish release notes after a fix is available. Reporters may be credited with their permission.
