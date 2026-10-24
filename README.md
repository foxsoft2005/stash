# Stash: "Where did I put it?"

[![Android CI](https://github.com/foxsoft2005/stash/actions/workflows/android.yml/badge.svg)](https://github.com/foxsoft2005/stash/actions/workflows/android.yml)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

Snap a photo when you put something away. Months later, ask *"where's my passport?"* and get the answer: **Top drawer, blue box**.

Everything stays on the phone. There's no account, no server, and no `INTERNET` permission, so Stash can't open a network connection at all. Android's Google Drive backup is switched off for the app's data too; only direct phone-to-phone transfer (Android 12+) can copy your stash to a new phone.

<details><summary>How that's enforced, and what still talks to Google</summary>

- **ML Kit's libraries ask for `INTERNET`** (to upload usage statistics to Google). Stash strips it from the built app, CI fails if any APK contains it again, and the release workflow refuses to publish one. CI also runs ML Kit offline on an emulator and forces its upload jobs, to prove that just fails quietly.
- **Gemini Nano isn't part of Stash.** It's run by **AICore**, a Google system app on supported phones. If you agree to the download, Stash asks AICore to fetch the model; AICore does the download itself. Photos are passed to AICore on the phone for recognition, never to the network by Stash. What AICore does is up to Google: they describe it as on-device processing inside Android's Private Compute Core.
- `ACCESS_NETWORK_STATE` is still present (added by ML Kit): it can only tell whether the phone is online, and Android 14+ requires it for the background jobs ML Kit schedules.
</details>

## Install

Download the latest `stash-x.y.z.apk` from [**Releases**](https://github.com/foxsoft2005/stash/releases) and open it on your phone. Android will ask you to allow *Install unknown apps* for your browser or file manager. Requires Android 8.0 or newer.

- **Updates:** Stash has no internet permission, so it can't check for updates itself. Add `https://github.com/foxsoft2005/stash` to [Obtainium](https://github.com/ImranR98/Obtainium) to get them automatically.
- **Check it's genuine:** every release lists the APK's SHA-256 and the signing certificate's fingerprint. The certificate is the same for every release, and must be:
  ```
  cb56a5796955715cf01eb4f1f1341d3b26f7e8e87dbaf677cf3744c207ceb491
  ```
  Check a downloaded APK with `apksigner verify --print-certs stash-x.y.z.apk` (Android SDK build-tools) or the [AppVerifier](https://github.com/soupslurpr/AppVerifier) app. Android itself refuses an update signed with a different certificate.
- **Brazil, Indonesia, Singapore, Thailand:** since 30 Sept 2026 Android requires apps from developers registered with Google. Stash isn't registered yet, so there you'll need Android's *advanced flow* for unverified apps (or `adb install`). Google plans to extend this worldwide in 2027.

## How it works

1. **Capture (under 3 s).** Long-press the app icon and choose **Stash something**, and the camera opens directly. Take the photo, pick a recent place chip, and tap **Save**.
2. **On-device AI tags the photo while you type.** Gemini Nano (multimodal, through the ML Kit GenAI Prompt API) names the object and adds synonyms as tags, for example *passport → document, travel, id*. Phones without Nano fall back to ML Kit's bundled image-labeling model. Nano is a large download, so Stash asks once, at the first photo; if you say no it keeps using the image labels, and you can still turn Nano on later from the edit screen ("Get smarter suggestions with Gemini Nano").
3. **Ask in plain words.** Search accepts natural questions in English or Russian, ignores filler words, and tolerates plural and case endings (keys/key, паспорта/паспорт). The best match is shown as a big answer card.
4. **Lending.** Enter who borrowed an item, then use the **Lent out** filter to see everything that's out. Searching "what did Mark borrow" also works.
5. **Encrypted backup.** **⋮ › Back up…** saves every item and photo into one password-protected `.stash` file, wherever you choose in the system file picker (Downloads, a USB stick, a cloud drive app). **⋮ › Restore…** merges a backup back in: new items are added, nothing already on the phone is changed or deleted, and restoring the same file twice is harmless. A forgotten password **can't be recovered**. A card on the home screen suggests a backup once the stash is worth keeping (5+ items, or a week after the first) and again when the last backup is 30+ days old and something changed; **Later** hides it for a week.

## Stack (current as of Sept 2026)

| Area | Choice |
|---|---|
| Build | AGP 9.3 with **built-in Kotlin** (no `kotlin-android` plugin), Gradle 9.6.1, version catalog, Kotlin 2.4.20, KSP2 |
| UI | Jetpack Compose (BOM 2026.09.00), Material 3 with dynamic color, edge-to-edge, state-based `TextFieldState` text fields |
| Navigation | **Navigation 3** 1.2.0: back stack is a list of `@Serializable NavKey`s you own; ViewModels scoped per entry via `lifecycle-viewmodel-navigation3`; predictive back is built in |
| Storage | **Room 3.0** (`androidx.room3`, suspend/Flow-only DAOs) with `BundledSQLiteDriver` |
| Camera | CameraX 1.6 with the Compose-native `CameraXViewfinder` (no `AndroidView`) |
| AI | ML Kit **GenAI Prompt API** (Gemini Nano via AICore), plus ML Kit Image Labeling fallback |
| Images | Coil 3 |

## Project layout

```
app/src/main/java/io/github/foxsoft2005/stash/
├── StashApp.kt              Application + tiny manual DI container
├── MainActivity.kt          edge-to-edge, handles the capture shortcut
├── data/                    Room 3 entity/DAO/DB, repository, private photo store
├── ai/                      SmartTagger → GeminiNanoTagger, falling back to MlKitLabelTagger
├── search/ItemSearch.kt     offline natural-question search (unit-tested)
└── ui/
    ├── StashNavigation.kt   Navigation 3 routes + NavDisplay
    ├── home/                search bar, answer card, photo grid, lent-out filter
    ├── capture/             CameraX viewfinder + shutter
    └── edit/                new/edit form, AI suggestions, recent-place chips
```

## Backup file format (`.stash`)

An encrypted ZIP: `manifest.json` (items, with explicit stable JSON keys) plus `photos/*.jpg`.

- Key: PBKDF2-HMAC-SHA256, 600 000 iterations, random 16-byte salt per file.
- Encryption: AES-256-GCM in 1 MiB segments (the STREAM construction, as in Tink's `AesGcmHkdfStreaming`), so photos are never all held in memory. Each segment's nonce encodes its index and a last-segment flag, and the header is authenticated with every segment, so reordered, swapped, cut-off or edited files are rejected.
- Restore only touches the database after the whole file has been decrypted and authenticated into a private staging folder.
- Code: `data/backup/` (`BackupCrypto`, `BackupArchive`, `BackupMerge`, `BackupManager`), unit-tested in `app/src/test/.../backup/`.

## Run it

1. Open the folder in the latest stable Android Studio and let Gradle sync.
2. Run on a device. The camera works on emulators, but Gemini Nano needs a supported phone (Pixel 9/10, Galaxy S24+ and others). On other devices the app automatically uses ML Kit labels.
3. Run `./gradlew test` for the unit tests (search, tags, backup format, reminders, …).
4. After any build, commit new files in `app/schemas/`. Room exports a JSON snapshot of each database version there, and migrations are checked against them.

### Releases
Pushing a tag like `v0.2.0` builds a signed APK and publishes it as a GitHub Release; see [RELEASING.md](RELEASING.md).

### CI
Every pull request and push to `main` runs **Android CI** (`.github/workflows/android.yml`): debug and (unsigned) release builds, unit tests, lint, and a check that `app/schemas/` is committed after database changes. Each run attaches the debug APK (*Summary → Artifacts*) for installing on a phone with `adb install -r app-debug.apk`, plus test and lint reports.

### Notes and caveats
- The ML Kit GenAI Prompt API is in **beta** (`1.0.0-beta4`), so its API can change.
- If a Compose library asks for a newer `compileSdk` minor version (37.1), bump it in `app/build.gradle.kts`.

## Good next steps
- **Voice capture**: "Passport, top drawer" spoken, then parsed by Nano into what + where.
- **Home-screen widget** (Glance) with a one-tap camera button and a search box.
- **Rooms and containers** as a hierarchy (House › Bedroom › Wardrobe › Blue box).
- **Semantic search** with on-device text embeddings, so "ID documents" finds "passport" even without matching tags.

## Languages

English and Russian. The app follows the phone's language; on Android 13+ you can also pick it per app (*Settings › Apps › Stash › Language*). Search understands questions in both languages either way.

To add a language: copy `app/src/main/res/values/strings.xml` to `values-<code>/strings.xml`, translate it, and add the language to `TranslationsTest` with its plural forms. The test (and CI) fails on missing strings, mismatched placeholders or missing plural forms.

## Contributing

Issues and pull requests are welcome. Every PR is built and tested by **Android CI**; please make sure its check is green. Dependency updates arrive weekly as grouped Dependabot PRs.

Found a security problem? Please report it privately; see [SECURITY.md](SECURITY.md).

## License

Copyright 2026 foxsoft2005

Licensed under the [Apache License, Version 2.0](LICENSE). You may use, modify and distribute this code, including commercially, as long as you keep the license and copyright notices and state your changes. It comes with no warranty.
