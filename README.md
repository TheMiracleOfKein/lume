# Lume

[![CI](https://github.com/TheMiracleOfKein/lume/actions/workflows/ci.yml/badge.svg)](https://github.com/TheMiracleOfKein/lume/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/TheMiracleOfKein/lume?label=release)](https://github.com/TheMiracleOfKein/lume/releases/latest)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#download)
[![License: Apache 2.0](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

<img src="branding/lume_icon_320.png" alt="Lume icon" width="96" align="right">

Lume is an Android reader for manga, manhwa and webtoons, written in Kotlin and Jetpack Compose.

- **It remembers exactly where you stopped**, inside a tall webtoon page and separately for every chapter.
- **One title can come from several sources.** Lume fills gaps in the chapter list from your other sources and reads a chapter from another source when its own fails.
- **Sources are built in.** Lume doesn't load extension APKs; supported site families can be added as small JSON packs.
- **No account, no ads, no analytics.** Everything stays on your phone unless you sign in to a tracker.

## Download

Get the APK from the [latest release](https://github.com/TheMiracleOfKein/lume/releases/latest). Lume needs Android 8.0 or newer.

- Installing a new version over the old one keeps your library, progress and downloads.
- If Android blocks the install, allow installs from your browser or file manager.

Release APKs are signed with the certificate `CN=Lume App`. To check a download, compare its SHA-256 fingerprint with this one:

```
b1594cec70301bfd0f66d73d82e9bdcea6355b8df8d2abe50e1fc022629bd565
```

```bash
apksigner verify --print-certs lume-vX.Y.Z.apk
```

## Features

**Reader**
- Webtoon (vertical) and paged modes, with seamless chapters, tap zones and volume-key paging.
- Fullscreen reading: the controls and system bars hide while you read.
- Exact resume inside a page, after rotation, after Android closes the app and across chapters.
- Page bookmarks with notes, shown on the title screen.
- Night dimming, side margins for webtoons, keep the screen on.

**Library and updates**
- Grid, compact and list views; sorting, filters, search and categories.
- Unread, downloaded and source counts on every title.
- Select several titles to move, mark, download or remove them.
- An Updates screen with new chapters from the whole library, background checks and notifications.
- A "Continue reading" home-screen widget and launcher shortcuts.

**One title, many sources**
- When you add a title, Lume looks for it on your other sources and suggests them.
- Linked sources fill the chapter list; every chapter shows which source it comes from.
- A chapter that fails to load (site check, region block, removed) opens from another source.
- Notifications name the source that published a new chapter first.
- Move a title to another source in one tap, with progress, bookmarks, categories, trackers and downloads.

**Sources**
- 12 built-in sources (11 sites in English and Russian, plus local files; listed below), plus your own JSON packs for Madara, LibSocial and HiveToons sites.
- Global search with per-source results, filters and history.
- Open a title link from the browser or share it to Lume.
- Cloudflare and DDoS-Guard checks are solved in a built-in browser; sign-in for MangaLib, YaoiLib and HentaiLib.

**Downloads and offline**
- Background downloads with a notification; pause, reorder, retry.
- Save chapters as CBZ, on internal storage or an SD card.
- Download new chapters automatically; delete chapters after reading.
- Import comics from a folder on your phone (with `ComicInfo.xml` and covers).

**Trackers**
- AniList, Shikimori, MyAnimeList, Kitsu and MangaUpdates.
- Progress updates when you finish a chapter; edit status, score and dates; link titles automatically when you add them.

**Backups**
- Save backups anywhere, or let Lume make them on a schedule.
- Import a Mihon or Tachiyomi backup (`.tachibk`): titles from sources Lume has, categories, tracker links and read chapters.

**App**
- English and Russian.
- System, light, dark and pure-black themes; Material You colours; a navigation rail on tablets.
- Incognito mode, app lock and hiding content in the recent-apps screen.
- DNS over HTTPS.

## Built-in sources

| Source | Language | Engine |
| --- | --- | --- |
| MangaDex | many | `mangadex` |
| MangaKatana | English | `mangakatana` |
| Asura Scans | English | `asura` |
| MangaRead | English | `madara` |
| HiveToons, Vortex Scans | English | `hivetoons` |
| MangaLib, YaoiLib\*, HentaiLib\* | Russian | `libsocial` |
| ReManga | Russian | `remanga` |
| MangaBuff | Russian | `mangabuff` |
| Local files | — | `local` |

\* Adult content; can be hidden from global search. HentaiLib shows chapters only to signed-in users (sign in under Sources).

How engines and packs work, and how to add a site: [docs/ENGINES.md](docs/ENGINES.md) and [docs/packs/README.md](docs/packs/README.md).

## Build from source

You need JDK 17 or newer (CI uses 21) and the Android SDK with API 37.

1. Copy [`local.properties.example`](local.properties.example) to `local.properties` and set `sdk.dir` (Android Studio does this for you).
2. Build a debug APK:

   ```bash
   ./gradlew :app:assembleDebug
   ```

   On Windows, use `gradlew.bat`. The APK is in `app/build/outputs/apk/debug/`.
3. Run the same checks as CI:

   ```bash
   ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
   ```

   Add `-Plume.live` to check the built-in sources against the live sites ([details](docs/ENGINES.md#live-source-checks)).

### Tracker keys (optional)

AniList, Shikimori and MyAnimeList sign-ins need *application* keys that identify the app to the tracker. Users still sign in with their own accounts. Kitsu and MangaUpdates need no keys.

- Put the keys in `local.properties` (`LUME_*`, see the example file). They are never committed.
- Without keys, everything works except sign-in to those three trackers.
- Keys built into an APK can be extracted, so register your own apps on the tracker sites for a fork or personal build.

## Documentation

- [Roadmap](docs/ROADMAP.md) and [release notes](docs/releases/)
- [Architecture](docs/ARCHITECTURE.md)
- [Catalogue engines](docs/ENGINES.md) and [source packs](docs/packs/README.md)
- [Why Lume doesn't load Mihon extensions](docs/MIHON_EXTENSIONS.md)
- [Translations](docs/I18N.md)
- [Device testing checklist](docs/DEVICE_TESTING.md)
- [Contributing](CONTRIBUTING.md), [privacy](PRIVACY.md), [security](SECURITY.md)

## Disclaimer

Lume does **not** host manga or comics. Catalogues and images come from third-party websites and APIs, and Lume is not affiliated with them. You are responsible for following the laws and terms that apply where you use the app.

## License

Licensed under the [Apache License 2.0](LICENSE).

Copyright 2026 TheMiracleOfKein
