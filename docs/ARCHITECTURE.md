# Architecture

Lume is a single Android app module (`:app`) plus `:baselineprofile`, which records the startup profile. Everything below is under `app/src/main/java/com/lume/app/`.

## Layers

```
ui/         Compose screens, ViewModels, navigation, theme, widget
  │  reads StateFlows, calls repositories
data/       repositories, Room, DataStore, workers, network, source engines, trackers
  │  uses
domain/     plain Kotlin models and pure logic (no Android): unit-tested on the JVM
```

- **`AppContainer`** builds every shared object once per process: HTTP client, database, preferences, source registry, repositories, trackers. Composables get it through `LocalAppContainer`, ViewModels through their constructor, workers and activities through `LumeApp.container`.
- **ViewModels** hold screen state in `mutableStateOf`; database streams are exposed as `StateFlow`s.
- **Pure logic** lives in `domain/` (and a few pure files such as `ui/screens/ReaderPosition.kt`) so it can be tested without a device.

## Main pieces

| Area | Where | What it does |
| --- | --- | --- |
| Sources | `data/source/` | `SourceRegistry` loads the bundled and imported JSON manifests and creates a Kotlin engine for each (`SourceEngines`). See [ENGINES.md](ENGINES.md). |
| Network | `data/network/` | `AppHttp` (OkHttp): one User-Agent, WebView-shared cookies, per-host rate limits, retries, DNS over HTTPS, readable errors. `CloudflareChallenge` opens a WebView only for real challenges. |
| Titles | `data/title/` | `TitleRepository` loads a title and its chapters (network first, offline cache as fallback), merges linked sources and loads pages with fallback to other copies. `TitleSourceRepository` finds, links, unlinks and migrates sources. |
| Library | `data/library/` | `LibraryRepository` (membership, categories, read marks), `LibraryChapters` (which chapters are new, by id), `LibraryUpdateWorker` (background checks, notifications). |
| Downloads | `data/downloads/` | `DownloadQueue` (Room-backed queue), `DownloadWorker` (foreground job), `DownloadStore` (files, CBZ, storage choice). |
| Backups | `data/backup/` | `BackupRepository` (Lume's JSON backups, versioned; format in [BACKUP_FORMAT.md](BACKUP_FORMAT.md)), `MihonBackup` (`.tachibk` import), `BackupWorker` (scheduled backups). |
| Logs | `data/ErrorLog.kt`, `data/CrashLog.kt` | Recent source errors and saved crashes, kept only on the phone; About → "Share log" hands them to an app the user picks. |
| Trackers | `data/tracker/` | One class per tracker behind the `Tracker` interface; `TrackerSync` pushes progress when a chapter is finished. |
| Continuity | `data/continuity/`, `ui/widget/` | Launcher shortcuts and the "Continue reading" widget. |
| Preferences | `data/prefs/` | `AppPreferences` over DataStore; account tokens never go into backups. |
| Reader | `ui/screens/Reader*` | Webtoon and paged modes, chapter-relative positions, page bookmarks. |
| Nearby | `data/nearby/` | Sending downloads to another phone on the same network: discovery, encrypted channel, transfer. `NearbyQr` builds and parses the sender's QR link (`lume-nearby://host:port?name=...`, no secret in it) and renders it with ZXing. |

## Database

Room database `lume.db` (`data/db/Database.kt`), version 9. Schemas are exported to `app/schemas/` and every upgrade is an additive migration in `LumeDatabase.MIGRATIONS`, tested by `MigrationTest`.

Main tables:
- `bookmarks`: library titles. `categories`, `title_prefs`, `title_meta`.
- `reading_progress` (last chapter per title), `chapter_progress` (exact spot per chapter), `chapter_read`, `page_bookmarks`.
- `library_chapters`: known chapters, to detect new ones.
- `downloads`, `download_queue`.
- `offline_title_cache`: the last loaded copy of each title.
- `tracker_links`, `source_packs`.
- `title_sources`: linked, suggested and dismissed other sources of a title.

### Linked chapters

A chapter that comes from a linked source keeps its place inside the main title with an id of the form `lume-link:<source>|<title>|<chapter>` (`domain/TitleSources.kt`). Read marks, positions, page bookmarks and downloads therefore need no special handling; `TitleRepository.loadPages` resolves the real source.

## Background work

WorkManager runs library checks (`LibraryUpdateWorker`), downloads (`DownloadWorker`, foreground with a notification) and scheduled backups (`BackupWorker`). Start-up work (loading sources, recovering downloads from disk, scheduling) runs in `AppContainer.start()`.

## Third-party code for QR codes

Nearby sending uses ZXing, with nothing from Google Play services, so F-Droid builds keep it.

- `com.google.zxing:core` (Apache-2.0): encodes the sender's QR code and decodes what the camera sees.
- `com.journeyapps:zxing-android-embedded` (Apache-2.0): the camera scanner screen (`ScanContract`). Chosen over CameraX plus a ZXing analyzer because it is a single small library with no Play services, and it needs no extra camera preview code. Its scanner activity is declared in the manifest to follow the phone's rotation.
- Camera access: the `CAMERA` permission is asked only when "Scan QR code" is tapped, after a short explanation; `android.hardware.camera` is `required="false"`, and phones without a camera don't see the button.

## Tests

- JVM unit tests in `app/src/test/`: domain logic, parsing helpers, backups, trackers, reader positions.
- `app/src/sharedTest/`: tests that run both on Robolectric and on a device (Room migrations).
- `LiveSourcesTest`: opt-in checks of every bundled source against the real site (`-Plume.live`); also runs weekly on CI.
- `:baselineprofile`: profile generation and a startup benchmark (needs a device).
