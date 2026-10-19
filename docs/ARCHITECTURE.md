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
| Backups | `data/backup/` | `BackupRepository` (Lume's JSON backups, versioned), `MihonBackup` (`.tachibk` import), `BackupWorker` (scheduled backups). |
| Trackers | `data/tracker/` | One class per tracker behind the `Tracker` interface; `TrackerSync` pushes progress when a chapter is finished. |
| Continuity | `data/continuity/`, `ui/widget/` | Launcher shortcuts and the "Continue reading" widget. |
| Preferences | `data/prefs/` | `AppPreferences` over DataStore; account tokens never go into backups. |
| Reader | `ui/screens/Reader*` | Webtoon and paged modes, chapter-relative positions, page bookmarks. |

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

## Tests

- JVM unit tests in `app/src/test/`: domain logic, parsing helpers, backups, trackers, reader positions.
- `app/src/sharedTest/`: tests that run both on Robolectric and on a device (Room migrations).
- `LiveSourcesTest`: opt-in checks of every bundled source against the real site (`-Plume.live`); also runs weekly on CI.
- `:baselineprofile`: profile generation and a startup benchmark (needs a device).
