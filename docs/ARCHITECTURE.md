# Architecture

Lume is being split into Gradle modules so that builds can include only the reading modes they need:

- `:core:model`: plain Kotlin (no Android). Holds `domain/` (models, the source contract, filters, reading logic). Its tests are in `core/model/src/test/`.
- `:core:common`: Android library with namespace `com.lume.app`. Holds all resources (`res/`), `util/` and `BuildConfig` (version and OAuth ids), so `com.lume.app.R` and `com.lume.app.BuildConfig` resolve unchanged everywhere. The version lives in `gradle.properties` (`lume.versionCode`, `lume.versionName`). `:app` has namespace `com.lume.app.shell` (applicationId stays `com.lume.app`).
- `:core:data`: Android library (namespace `com.lume.app.data`, packages stay `com.lume.app.*`). Holds `data/` (Room, repositories, the source registry, downloads (CBZ through the zip-only `ArchiveReaders`), the folder import, trackers, sync, workers), `AppContainer`, `ui/nav/Routes` and `AppLinks` (route strings and notification intents), and the `ContinueWidget` provider. The Application implements `ContainerOwner` so workers reach the container; `AppContainer` takes the installed content modules as a parameter. Its tests are in `core/data/src/test/`, the Room migration tests in `core/data/src/sharedTest/`, and the exported schemas in `core/data/schemas/`.
- `:core:ui`: Android library (namespace `com.lume.app.ui`, packages stay `com.lume.app.*`) with Compose enabled. Holds all of `ui/` (screens, settings, components, theme, shell, navigation helpers, `TrackerAuthActivity`, `SourceLoginActivity`) and `LocalAppContainer`. Feature modules add their screens to the navigation graph through `ContentScreens` (in `ui/shell/`), which `MainActivity` passes to `LumeRoot`. It depends on `:core:data` (`api`) and gets the container from the Application through `ContainerOwner`. Its tests (view models, theme, Roborazzi screenshots) are in `core/ui/src/test/`, the golden images in `core/ui/src/test/screenshots/`.
- `:feature:comics`: Android library (namespace `com.lume.app.comics`, packages stay `com.lume.app.*`). Holds the image reader (`ui/screens/Reader*`, `TallPageTiles`), `ComicsScreens`, which registers the reader route, and `ComicsModule` with the site and server engines (`data/source/*Engine.kt`, `OpdsFeed`) and the bundled source manifests (`src/main/assets/sources/`), plus local files: `LocalEngine`, the CBR and CB7 readers (`ComicArchives`) and the PDF renderer (`data/local/pdf/`). Telephoto, junrar, Commons Compress and xz are only on its classpath. It depends on `:core:ui`. Its tests (engine parsing on saved fixtures in `src/test/resources/fixtures/`, archives and PDF, the opt-in `LiveSourcesTest`, reader view model, positions, tiles, the reader bars screenshots) are in `feature/comics/src/test/`, the golden images in `feature/comics/src/test/screenshots/`.
- `:feature:novel`: Android library (namespace `com.lume.app.novel`). For now only `NovelModule` (the ranobe mode, no engines yet); the text reader and novel sources come after 1.0. It depends on `:core:data`.
- `:app`: only `LumeApp`, `MainActivity`, the manifest, signing and the baseline profile, under `app/src/main/java/com/lume/app/`. `SmokeTest` stays in `app/src/androidTest/`.
- `:baselineprofile`: records the startup profile (on the `full` flavor).

### Build flavors

`:app` has one flavor dimension, `modes`. Every flavor has the same `applicationId` and signing key, so switching builds keeps the user's data.

| Flavor | Modules | Release asset |
| --- | --- | --- |
| `full` | `:feature:comics`, `:feature:novel` | `lume-vX.Y.Z-full.apk` |
| `manga` | `:feature:comics` | `lume-vX.Y.Z-manga.apk` |

Each flavor's source set has its own `InstalledModules.kt` (`app/src/<flavor>/java/com/lume/app/`): the flavor name, the `ContentModule`s for `AppContainer` and the `ContentScreens` for `LumeRoot`. It is a plain list (no reflection), so R8 keeps exactly what the flavor ships. The in-app updater downloads only the APK named for its own flavor (`AppUpdater.pickApk`). At start, an active mode whose module the build lacks falls back to the first installed one; that mode's rows stay in the database. Tasks are per flavor: `assembleFullDebug`, `testMangaDebugUnitTest`, `lintFullDebug`, and so on.

### Reading modes

`AppPreferences.activeMode` is the mode the shared screens show; Library and History filter by it. When a build ships more than one mode (`canSwitchModes`), a long press on the logo in the bottom bar or the rail opens `ModeSwitcher` (`ui/shell/ModeSwitcher.kt`), with haptic feedback and a TalkBack action label; the menu has a "Mode" entry too. Picking a mode stores it and resets navigation to Home. A module marked `preview` (the novel module in 1.0) shows `ModeComingSoon` on Home, Catalog, Updates and Search while no loaded source serves its kind (`ModeGate`). The manga build has one mode, so it has no long press, switcher or menu entry.

Package names are unchanged (`com.lume.app.domain`), so code doesn't care which module a file lives in.

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
- **Pure logic** lives in `domain/` (and a few pure files such as `ui/screens/ReaderPosition.kt` in `:feature:comics`) so it can be tested without a device.

## Main pieces

| Area | Where | What it does |
| --- | --- | --- |
| Sources | `data/source/` | `SourceRegistry` loads the bundled and imported JSON manifests and creates a Kotlin engine for each from the installed `ContentModule`s (`SourceEngines`); the engines themselves are in `:feature:comics`. See [ENGINES.md](ENGINES.md). |
| Network | `data/network/` | `AppHttp` (OkHttp): one User-Agent, WebView-shared cookies, per-host rate limits, retries, DNS over HTTPS, readable errors. `CloudflareChallenge` opens a WebView only for real challenges. |
| Titles | `data/title/` | `TitleRepository` loads a title and its chapters (network first, offline cache as fallback), merges linked sources and loads pages with fallback to other copies. `TitleSourceRepository` finds, links, unlinks and migrates sources. |
| Library | `data/library/` | `LibraryRepository` (membership, categories, read marks), `LibraryChapters` (which chapters are new, by id), `LibraryUpdateWorker` (background checks, notifications). |
| Downloads | `data/downloads/` | `DownloadQueue` (Room-backed queue), `DownloadWorker` (foreground job), `DownloadStore` (files, CBZ, storage choice). |
| Backups | `data/backup/` | `BackupRepository` (Lume's JSON backups, versioned; format in [BACKUP_FORMAT.md](BACKUP_FORMAT.md)), `MihonBackup` (`.tachibk` import), `BackupWorker` (scheduled backups). |
| Logs | `data/ErrorLog.kt`, `data/CrashLog.kt` | Recent source errors and saved crashes, kept only on the phone; About → "Share log" hands them to an app the user picks. |
| Trackers | `data/tracker/` | One class per tracker behind the `Tracker` interface; `TrackerSync` pushes progress when a chapter is finished. |
| Continuity | `data/continuity/`, `ui/widget/` | Launcher shortcuts and the "Continue reading" widget. |
| Preferences | `data/prefs/` | `AppPreferences` over DataStore; account tokens never go into backups. |
| Reader | `ui/screens/Reader*` (`:feature:comics`) | Webtoon and paged modes, chapter-relative positions, page bookmarks. |
| Nearby | `data/nearby/` | Sending downloads to another phone on the same network: discovery, encrypted channel, transfer. `NearbyQr` builds and parses the sender's QR link (`lume-nearby://host:port?name=...`, no secret in it) and renders it with ZXing. |

## Database

Room database `lume.db` (`data/db/Database.kt`), version 9. Schemas are exported to `core/data/schemas/` and every upgrade is an additive migration in `LumeDatabase.MIGRATIONS`, tested by `MigrationTest`.

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
- `com.journeyapps:zxing-android-embedded` (Apache-2.0): the camera scanner screen (`ScanContract`). Chosen over CameraX plus a ZXing analyzer because it is a single small library with no Play services, and it needs no extra camera preview code. Its scanner activity is overridden in the manifest to drop the library's landscape lock.
- Camera access: the `CAMERA` permission is asked only when "Scan QR code" is tapped, after a short explanation; `android.hardware.camera` is `required="false"`, and phones without a camera don't see the button.

## Tests

- JVM unit tests in `core/model/src/test/` (domain logic) , `core/data/src/test/` (parsing helpers, backups, trackers, domain tests that need data classes) and `core/ui/src/test/` (view models, reader positions, theme, screenshots).
- `core/data/src/sharedTest/`: tests that run both on Robolectric and on a device (Room migrations).
- `LiveSourcesTest`: opt-in checks of every bundled source against the real site (`-Plume.live`); also runs weekly on CI.
- `:baselineprofile`: profile generation and a startup benchmark (needs a device).
- `app/src/androidTest/`: instrumented tests; `SmokeTest` starts the app and opens Home, Library and Settings. `.github/workflows/emulator.yml` runs it, together with the Room migration test, on an API 34 emulator (`reactivecircus/android-emulator-runner`, KVM, cached AVD snapshot). Locally: `./gradlew :app:connectedDebugAndroidTest` with a device or emulator attached.

### Screenshot tests

Roborazzi (on the Robolectric setup) renders Home, Library (grid, list, empty), Title details, Chapter list, Settings and the Reader bars in light, dark and large font (font scale 2.0), from fixed data with no network. They live in `core/ui/src/test/java/com/lume/app/ui/screenshots/` (the Reader bars in `feature/comics/src/test/java/com/lume/app/ui/screenshots/`, with its own copy of `ScreenshotTest`) and use the stateless composables of each screen (`HomeFeedList`, `LibraryContent`, `TitleHeader`, `ChapterRow`, `SettingsSections`, `ReaderTopBar`/`ReaderBottomBar`). The golden images are in each module's `src/test/screenshots/`.

- A plain `testDebugUnitTest` only composes the screens; it does not compare images.
- `./gradlew :core:ui:verifyRoborazziDebug :feature:comics:verifyRoborazziDebug` compares against the goldens (up to 1% of pixels may differ, to absorb text anti-aliasing between operating systems). CI runs it on Linux; on failure it uploads the images as drawn on CI as an artifact.
- After a deliberate UI change, re-record and commit the images: `./gradlew :core:ui:recordRoborazziDebug :feature:comics:recordRoborazziDebug --tests "com.lume.app.ui.screenshots.*"`, then look at the changed PNGs in the diff.
- New screens: add a test class that extends `ScreenshotTest` and calls `shoot("name", Look.Light) { ... }` for each look.
