# Lume

[![CI](https://github.com/TheMiracleOfKein/lume/actions/workflows/ci.yml/badge.svg)](https://github.com/TheMiracleOfKein/lume/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/TheMiracleOfKein/lume?label=release)](https://github.com/TheMiracleOfKein/lume/releases/latest)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#download)
[![License: Apache 2.0](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

<img src="branding/lume_icon_320.png" alt="Lume icon" width="96" align="right">

Lume is an Android reader for **manga, manhwa and webtoons** and for **light novels, web novels and books**, in one app. It's written in Kotlin and Jetpack Compose.

- **Two reading modes.** Manga mode for anything drawn, Ranobe mode for text. Each has its own sources, library and reader; one long press switches between them.
- **It remembers exactly where you stopped**: inside a tall webtoon page, on the paragraph of a novel, and separately for every chapter.
- **One title from many sources.** Lume fills gaps in a manga's chapter list from your other sources, and reads a chapter from another source when its own fails.
- **Novels you can listen to and translate.** Read aloud with any voice on your phone; translate chapters between English and Russian online, with your own AI key, or offline on the phone.
- **Sources are built in.** No extension APKs to install. Supported site families can be added as small JSON packs.
- **No account, no ads, no analytics.** Everything stays on your phone unless you turn on a tracker, sync or an online service.

**New to Lume?** Start with the [user guide](docs/guide/README.md).

## Download

Get the APK from the [latest release](https://github.com/TheMiracleOfKein/lume/releases/latest). Lume needs Android 8.0 or newer.

| APK | Reads | For you if… |
| --- | --- | --- |
| `lume-vX.Y.Z-full.apk` | Manga **and** novels | you're not sure: this one has everything |
| `lume-vX.Y.Z-manga.apk` | Manga, manhwa, webtoons | you never read novels |
| `lume-vX.Y.Z-ranobe.apk` | Novels only | you never read manga |

- All three install over each other and keep your data. The in-app updater stays on the build you installed.
- Installing a new version over the old one keeps your library, progress and downloads.
- **Lume Translate** (`lume-translate-vX.Y.Z.apk`, from its own [releases](https://github.com/TheMiracleOfKein/lume/releases?q=translate)) is an optional add-on for translating novels offline. Lume offers to install it when you need it.
- If Android blocks the install, allow installs from your browser or file manager.

Release APKs are signed with the certificate `CN=Lume App`. To check a download, compare its SHA-256 fingerprint with this one:

```
b1594cec70301bfd0f66d73d82e9bdcea6355b8df8d2abe50e1fc022629bd565
```

```bash
apksigner verify --print-certs lume-vX.Y.Z-full.apk
```

Each APK also has a `.sha256` file next to it on the release page.

## Features

### Manga mode

**Reader** ([guide](docs/guide/manga-reader.md))
- Webtoon (vertical) and paged (left-to-right, right-to-left) modes, with seamless chapters, tap zones and volume-key paging.
- Sharp zoom: the part you zoom into is decoded again at full detail, even on very tall webtoon strips.
- Guided panel view: a tap moves from panel to panel, in manga or comics order. Panels are found on the phone, with no model.
- Cropped borders, double pages split in two, and two pages side by side in landscape.
- Page bookmarks with notes, shown on the title screen with the panel you marked.
- Night dimming, colour filters, E-ink mode, side margins for webtoons, auto-scroll.

**One title, many sources** ([guide](docs/guide/title-screen.md#linking-several-sources))
- When you add a title, Lume looks for it on your other sources and suggests linking them.
- Linked sources fill the chapter list; each chapter shows which source it comes from.
- A chapter that fails to load (site check, region block, removed) opens from another source.
- Move a title to another source in one tap, with progress, bookmarks, categories, trackers and downloads.

**Your own files and servers** ([guide](docs/guide/sources.md#local-files-and-watched-folders))
- Import CBZ, CBR, CB7, PDF or image folders (with `ComicInfo.xml` and covers), and watch folders for new chapters.
- OPDS catalogues, Komga and Kavita, with reading progress sent back to Komga and Kavita.

### Ranobe mode (novels)

**Sources and books** ([guide](docs/guide/novels.md))
- RanobeLib (Russian), Royal Road and Scribble Hub (English), with search, filters and downloads.
- Your own EPUB 2/3 and TXT files (encoding and chapters detected), from a file picker, a folder or a watched folder.

**Text reader**
- Scroll or page by page; font, size, line and paragraph spacing, margins, justified text, five themes.
- Settings saved per title if you like, and the paragraph you stopped at kept everywhere.

**Read aloud**
- Uses any text-to-speech engine on the phone (no Google services needed), with speed, voice and a sleep timer.
- Keeps playing with the screen off, with lock-screen and headphone controls, and highlights the sentence being read.

**Translation** ([guide](docs/guide/translation-and-ai.md))
- English ↔ Russian with Google (free), DeepL (your key), an AI service with your key (OpenRouter, OpenAI, DeepSeek, Gemini or your own server), or **offline on the phone** with the Lume Translate add-on.
- A glossary per title keeps names the same in every chapter; AI suggests new names as it meets them.
- Long-press a paragraph to see the original.

**Story help, without spoilers**
- Long-press a name to see where it first appeared and its latest mentions, offline, only from chapters you've reached.
- A codex of characters, places and groups per title.
- "Previously…": a recap when you come back after a break.
- Optional AI story notes: chapter summaries, a codex that fills itself, and "the story so far".

### Everywhere

**Library and updates** ([guide](docs/guide/library.md))
- Grid, compact and list views; sorting, filters, search and categories.
- An Updates screen with background checks and notifications.
- Reading statistics, a "Continue reading" widget and launcher shortcuts.

**Sources and search** ([guide](docs/guide/sources.md))
- Global search across all sources of the mode, with filters and history.
- Open a title link from the browser or share it to Lume.
- Cloudflare and DDoS-Guard checks are solved in a built-in browser; sign-in for the Lib sites.
- When a site moves, Lume can learn its new address without an app update.

**Downloads** ([guide](docs/guide/downloads.md))
- Background downloads; pause, reorder, retry. Save as CBZ, on internal storage, an SD card or any folder.
- Download new chapters automatically; delete chapters after reading.
- Send downloaded chapters to a nearby phone over Wi-Fi, without a server.

**Trackers** ([guide](docs/guide/trackers.md))
- AniList, Shikimori, MyAnimeList, Kitsu and MangaUpdates; novel lists on AniList, Shikimori and MyAnimeList.
- Progress updates when you finish a chapter; import your reading list.

**Sync and backups** ([guide](docs/guide/sync-and-backups.md))
- Sync library and progress between devices through a folder (Syncthing, a cloud drive) or WebDAV, optionally encrypted. No Lume account.
- Backups by hand or on a schedule, to any folder.
- Import from Mihon / Tachiyomi (`.tachibk`), Kotatsu (`.bk.zip`), a tracker list, or your MangaLib / RanobeLib bookmarks.

**App**
- English and Russian.
- System, light, dark and pure-black themes; Material You colours; a navigation rail on tablets.
- Incognito mode, app lock, hiding content in the recent-apps screen, encrypted sign-ins, DNS over HTTPS.

## Built-in sources

| Source | Mode | Language | Engine |
| --- | --- | --- | --- |
| MangaDex | Manga | many | `mangadex` |
| MangaKatana | Manga | English | `mangakatana` |
| Asura Scans | Manga | English | `asura` |
| MangaRead | Manga | English | `madara` |
| HiveToons, Vortex Scans | Manga | English | `hivetoons` |
| MangaLib, SlashLib\*, YaoiLib\*, HentaiLib\* | Manga | Russian | `libsocial` |
| ReManga | Manga | Russian | `remanga` |
| MangaBuff | Manga | Russian | `mangabuff` |
| InkStory | Manga | Russian | `inkstory` |
| RanobeLib | Ranobe | Russian | `ranobelib` |
| Royal Road | Ranobe | English | `royalroad` |
| Scribble Hub | Ranobe | English | `scribblehub` |
| Local files | Both | — | `local` |
| OPDS, Komga, Kavita (servers you add) | Manga | — | `opds`, `komga`, `kavita` |

\* Adult content; can be left out of global search. HentaiLib shows chapters only to signed-in users (sign in under Sources).

More sites come as JSON packs from the in-app catalogue (**Sources → Check packs and updates**), for example LikeManga and ToonGod. How engines and packs work, and how to add a site: [docs/ENGINES.md](docs/ENGINES.md) and [docs/packs/README.md](docs/packs/README.md).

## Build from source

You need JDK 17 or newer (CI uses 21) and the Android SDK with API 37.

1. Clone with submodules if you want to build the Lume Translate add-on: `git clone --recursive …` (or `git submodule update --init --recursive` later). Lume itself builds without them.
2. Copy [`local.properties.example`](local.properties.example) to `local.properties` and set `sdk.dir` (Android Studio does this for you).
3. Build a debug APK:

   ```bash
   ./gradlew :app:assembleDebug
   ```

   On Windows, use `gradlew.bat`. The APKs are in `app/build/outputs/apk/<flavor>/debug/` (`full`, `manga`, `ranobe`).
4. Run the same checks as CI:

   ```bash
   ./gradlew :core:model:test :core:common:testDebugUnitTest :core:data:testDebugUnitTest :core:ui:testDebugUnitTest :core:translate:testDebugUnitTest :feature:comics:testDebugUnitTest :feature:novel:testDebugUnitTest :app:assembleDebug :app:testFullDebugUnitTest :app:testMangaDebugUnitTest :app:testRanobeDebugUnitTest :app:lintFullDebug :app:lintMangaDebug :app:lintRanobeDebug
   ```

   Add `-Plume.live` to check the built-in sources against the live sites ([details](docs/ENGINES.md#live-source-checks)).

The code layout is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), and the rules for changes in [CONTRIBUTING.md](CONTRIBUTING.md).

### Tracker keys (optional)

AniList, Shikimori and MyAnimeList sign-ins need *application* keys that identify the app to the tracker. Users still sign in with their own accounts. Kitsu and MangaUpdates need no keys.

- Put the keys in `local.properties` (`LUME_*`, see the example file). They are never committed.
- Without keys, everything works except sign-in to those three trackers.
- Keys built into an APK can be extracted, so register your own apps on the tracker sites for a fork or personal build.

## Documentation

**Using Lume**
- [User guide](docs/guide/README.md): every screen and setting, for manga and novels
- [Release notes](docs/releases/) and [roadmap](docs/ROADMAP.md)
- [Privacy](PRIVACY.md) and [security](SECURITY.md)
- [Why Lume doesn't load Mihon extensions](docs/MIHON_EXTENSIONS.md)

**Working on Lume**
- [Contributing](CONTRIBUTING.md) and [architecture](docs/ARCHITECTURE.md)
- [Catalogue engines](docs/ENGINES.md), [source packs and `hosts.json`](docs/packs/README.md)
- [Backup format](docs/BACKUP_FORMAT.md), [translations](docs/I18N.md), [accessibility](docs/ACCESSIBILITY.md)
- [Releasing](docs/RELEASING.md), [device testing checklist](docs/DEVICE_TESTING.md), [pre-release check reports](docs/releases/checks/)

## Disclaimer

Lume does **not** host manga, comics or novels. Catalogues, images and texts come from third-party websites and APIs, and Lume is not affiliated with them. You are responsible for following the laws and terms that apply where you use the app.

## License

Licensed under the [Apache License 2.0](LICENSE). The Lume Translate add-on includes bergamot-translator (MPL-2.0); see [translate-addon/THIRD_PARTY.md](translate-addon/THIRD_PARTY.md).

Copyright 2026 TheMiracleOfKein
