# Architecture

Lume is split into Gradle modules so that each build includes only the reading modes it needs:

- `:core:model`: plain Kotlin (no Android). Holds `domain/` (models, the source contract, filters, reading logic). Its tests are in `core/model/src/test/`.
- `:core:common`: Android library with namespace `com.lume.app`. Holds all resources (`res/`), `util/` and `BuildConfig` (version and OAuth ids), so `com.lume.app.R` and `com.lume.app.BuildConfig` resolve unchanged everywhere. The version lives in `gradle.properties` (`lume.versionCode`, `lume.versionName`). `:app` has namespace `com.lume.app.shell` (applicationId stays `com.lume.app`).
- `:core:data`: Android library (namespace `com.lume.app.data`, packages stay `com.lume.app.*`). Holds `data/` (Room, repositories, the source registry, downloads (CBZ through the zip-only `ArchiveReaders`), the folder import, trackers, sync, workers), `AppContainer`, `ui/nav/Routes` and `AppLinks` (route strings and notification intents), and the `ContinueWidget` provider. The Application implements `ContainerOwner` so workers reach the container; `AppContainer` takes the installed content modules as a parameter. Its tests are in `core/data/src/test/`, the Room migration tests in `core/data/src/sharedTest/`, and the exported schemas in `core/data/schemas/`.
- `:core:ui`: Android library (namespace `com.lume.app.ui`, packages stay `com.lume.app.*`) with Compose enabled. Holds all of `ui/` (screens, settings, components, theme, shell, navigation helpers, `TrackerAuthActivity`, `SourceLoginActivity`) and `LocalAppContainer`. Feature modules add their screens to the navigation graph through `ContentScreens` (in `ui/shell/`), which `MainActivity` passes to `LumeRoot`. It depends on `:core:data` (`api`) and gets the container from the Application through `ContainerOwner`. Its tests (view models, theme, Roborazzi screenshots) are in `core/ui/src/test/`, the golden images in `core/ui/src/test/screenshots/`.
- `:feature:comics`: Android library (namespace `com.lume.app.comics`, packages stay `com.lume.app.*`). Holds the image reader (`ui/screens/Reader*`, `TallPageTiles`), `ComicsScreens`, which provides the comics reader, and `ComicsModule` with the site and server engines (`data/source/*Engine.kt`, `OpdsFeed`) and the bundled source manifests (`src/main/assets/sources/`), plus local files: `LocalEngine`, the CBR and CB7 readers (`ComicArchives`) and the PDF renderer (`data/local/pdf/`). Telephoto, junrar, Commons Compress and xz are only on its classpath. It depends on `:core:ui`. Its tests (engine parsing on saved fixtures in `src/test/resources/fixtures/`, archives and PDF, the opt-in `LiveSourcesTest`, reader view model, positions, tiles, the reader bars screenshots) are in `feature/comics/src/test/`, the golden images in `feature/comics/src/test/screenshots/`.
- `:feature:novel`: Android library (namespace `com.lume.app.novel`, packages stay `com.lume.app.*`) with Compose enabled. Holds `NovelModule` (the ranobe mode), `NovelScreens` (registers the novel reader), the text model (`data/novel/NovelBlocks`: chapter HTML to `Heading` / `Paragraph` (styled spans) / `Image` / `Divider` blocks), local books (`EpubBook`, `TxtBook`), `LocalNovelEngine` (`data/source/`), which reads `filesDir/local-novel/<Book>/` and serves the `local-novel` source, `RanobeLibEngine` (`ranobelib`, bundled manifest in this module's `assets/sources/`), the shared Lib client with chapter text, and `RoyalRoadEngine` and `ScribbleHubEngine` for the English novel sites. It also holds read aloud (`readaloud/`, a Media3 session around Android's text-to-speech), chapter translation (`translate/`), the AI client (`llm/`) and the story index, codex and recap. The text reader (`ui/screens/NovelReader*`) is described under "Novel reader" below. It depends on `:core:ui`, Coil and jsoup. Its tests (including Roborazzi screenshots) are in `feature/novel/src/test/`.
- `:core:translate`: the AIDL contract (`ILumeTranslator`, the signature permission `com.lume.permission.TRANSLATE`) and `ModelCatalog` with the pinned Firefox Translations models, shared by `:feature:novel` and the add-on.
- `:translate-addon`: the separate Lume Translate app (`com.lume.translate`), which builds bergamot-translator (git submodule under `translate-addon/third_party`) with the NDK for `arm64-v8a` and `x86_64` and translates offline for Lume. It has its own version and releases ([RELEASING.md](RELEASING.md)).
- `:app`: only `LumeApp`, `MainActivity`, the manifest, signing and the baseline profile, under `app/src/main/java/com/lume/app/`. `SmokeTest` stays in `app/src/androidTest/`.
- `:baselineprofile`: records the startup profile (on the `full` flavor).

### Build flavors

`:app` has one flavor dimension, `modes`. Every flavor has the same `applicationId` and signing key, so switching builds keeps the user's data.

| Flavor | Modules | Release asset |
| --- | --- | --- |
| `full` | `:feature:comics`, `:feature:novel` | `lume-vX.Y.Z-full.apk` |
| `manga` | `:feature:comics` | `lume-vX.Y.Z-manga.apk` |
| `ranobe` | `:feature:novel` | `lume-vX.Y.Z-ranobe.apk` |

Each flavor's source set has its own `InstalledModules.kt` (`app/src/<flavor>/java/com/lume/app/`): the flavor name, the `ContentModule`s for `AppContainer`, the `ContentScreens` for `LumeRoot` and the Coil components its modules bring (PDF pages for comics). The feature modules are flavor dependencies (`fullImplementation`, `mangaImplementation`, `ranobeImplementation`), and each flavor's `AndroidManifest.xml` lists the title links of its own sources. It is a plain list (no reflection), so R8 keeps exactly what the flavor ships. The in-app updater downloads only the APK named for its own flavor (`AppUpdater.pickApk`). At start, an active mode whose module the build lacks falls back to the first installed one; that mode's rows stay in the database. Tasks are per flavor: `assembleFullDebug`, `testMangaDebugUnitTest`, `lintFullDebug`, and so on.

### Reading modes

`AppPreferences.activeMode` is the mode the shared screens show; Library and History filter by it. When a build ships more than one mode (`canSwitchModes`), a long press on the logo in the bottom bar or the rail opens `ModeSwitcher` (`ui/shell/ModeSwitcher.kt`), with haptic feedback and a TalkBack action label; the menu has a "Mode" entry too. Picking a mode stores it and resets navigation to Home. A module marked `preview` (the novel module from 1.0 to 1.1; none since 1.2) shows `ModeComingSoon` on Home, Catalog, Updates and Search while no loaded source serves its kind (`ModeGate`). The manga and ranobe builds have one mode each, so they have no long press, switcher or menu entry.

Package names are unchanged (`com.lume.app.domain`), so code doesn't care which module a file lives in.

### Novel reader

The shell has one reader route and opens the reader of the title's kind: the source's kind, else the library bookmark's, else the kind stored in `reading_progress` (a History entry opened at cold start that isn't in the library), else comics (`readerKindFor`).

`NovelReaderViewModel` (`feature/novel`) loads the title through `app.titles.stream`, a chapter's text from its download (`DownloadStore.localText`) or else through `requireNovel().getChapterText` on IO, and parses it with `NovelBlocks` off the main thread. The list is a flat `List<NovelItem>`: each loaded chapter contributes a title row and its blocks (`NovelPosition.kt`). The position is a `NovelAnchor` (chapter, top block, fraction of that block scrolled past), kept in `SavedStateHandle`, saved 400 ms after scrolling stops and flushed on stop, Back and close in `appScope`, like the comics reader. It writes `reading_progress` (`mediaKind = "novel"`, `pageIndex` = block, `pageCount` = block count, `pageOffset` = fraction), `chapter_progress`, the read mark when a chapter's last row has been on screen, and reading-time sessions (`pagesRead` stays 0). Incognito saves none of it. When 15 rows or fewer remain below the screen, the next chapter is appended without changing the list session; previous / next / the chapter list reload the list.

`NovelReaderScreen` draws a `LazyColumn`. Restoring scrolls to the anchor's row, waits for that row's height, then scrolls to the fraction of it in pixels (once per session, also after rotation). Tap zones: the middle toggles the bars, the top and bottom thirds scroll about 90% of the viewport. Text settings (`NovelReaderSettings`, DataStore keys `novel_*`, every value clamped) and the reader-only colour themes (`NovelPalettes`, WCAG AA checked in `NovelPaletteContrastTest`) live apart from the comics reader's settings.

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
| Sources | `data/source/` | `SourceRegistry` loads the bundled and imported JSON manifests and creates a Kotlin engine for each from the installed `ContentModule`s (`SourceEngines`); the engines themselves are in `:feature:comics` and `:feature:novel`. The MangaLib-family API client shared by both (`LibSocialBase`: catalogue, filters, details, chapters with teams, bookmarks, links) is here; `LibSocialEngine` (comics) adds pages, `RanobeLibEngine` (novel) adds chapter text through `LibChapterText`. `SourceEngines.isLib` marks the family for the request limit and the Sources sign-in. The registry wrappers implement both `ComicSource` and `NovelSource` (`requireComic()` / `requireNovel()`), so an engine of either kind is reachable behind them. Local files: `LocalImporter` copies a picked folder into `filesDir/local/` (comics) and, when an installed module lists `localBookExtensions` (the novel module: `epub`, `txt`), each book file into `filesDir/local-novel/<file name>/`; the manga build lists none. "Import books…" (`OpenMultipleDocuments`, shown only when some module lists book extensions) calls `LocalImporter.importBooks`, which copies each picked `.epub` / `.txt` (by display name) to `local-novel/<name>/<name.ext>` through `.part` and a rename, skips other files, and skips a file of the same size already there (no ledger). See [ENGINES.md](ENGINES.md). |
| Network | `data/network/` | `AppHttp` (OkHttp): one User-Agent, WebView-shared cookies, per-host rate limits, retries, DNS over HTTPS, readable errors. `CloudflareChallenge` opens a WebView only for real challenges. |
| Titles | `data/title/` | `TitleRepository` loads a title and its chapters (network first, offline cache as fallback), merges linked sources and loads pages with fallback to other copies. `TitleSourceRepository` finds, links, unlinks and migrates sources. |
| Library | `data/library/` | `LibraryRepository` (membership, categories, read marks), `LibraryChapters` (which chapters are new, by id), `LibraryUpdateWorker` (background checks, notifications). |
| Downloads | `data/downloads/` | `DownloadQueue` (Room-backed queue), `DownloadWorker` (foreground job), `DownloadStore` (files, CBZ, storage choice). Text chapters (novels) are saved flat in the chapter folder as `chapter.html` plus `img-0000.<ext>`… with `<img src>` pointing at those names (`TextChapterFiles`); the row's paths are the HTML then the pictures, and `pageCount` counts the pictures. A picture that fails keeps its web link. `localText` reads one back for the novel reader; text rows are told apart by the source's kind, not the path. Nearby sending leaves them out. |
| Backups | `data/backup/` | `BackupRepository` (Lume's JSON backups, versioned; format in [BACKUP_FORMAT.md](BACKUP_FORMAT.md)), `MihonBackup` (`.tachibk` import), `BackupWorker` (scheduled backups). |
| Logs | `data/ErrorLog.kt`, `data/CrashLog.kt` | Recent source errors and saved crashes, kept only on the phone; About → "Share log" hands them to an app the user picks. |
| Trackers | `data/tracker/` | One class per tracker behind the `Tracker` interface; `TrackerSync` pushes progress when a chapter is finished. |
| Continuity | `data/continuity/`, `ui/widget/` | Launcher shortcuts and the "Continue reading" widget. |
| Preferences | `data/prefs/` | `AppPreferences` over DataStore; account tokens never go into backups. |
| Reader | `ui/screens/Reader*` (`:feature:comics`), `ui/shell/AppShell.kt` | The shell registers the one `Routes.Reader` route and opens the reader of the source's `MediaKind` (the source in `container.sources`, else the bookmark's kind, else comics); each module adds its reader with `ShellGraph.reader(kind)` through its `ContentScreens`. Comics: webtoon and paged modes, chapter-relative positions, page bookmarks. Comics and novels download; local sources don't (`DownloadQueue.canDownload`). |
| Nearby | `data/nearby/` | Sending downloads to another phone on the same network: discovery, encrypted channel, transfer. `NearbyQr` builds and parses the sender's QR link (`lume-nearby://host:port?name=...`, no secret in it) and renders it with ZXing. |

## Database

Room database `lume.db` (`data/db/Database.kt`), version 19. Schemas are exported to `core/data/schemas/` and every upgrade is an additive migration in `LumeDatabase.MIGRATIONS`, tested by `MigrationTest`.

Main tables:
- `bookmarks`: library titles. `categories`, `title_prefs`, `title_meta`.
- `reading_progress` (last chapter per title), `chapter_progress` (exact spot per chapter), `chapter_read`, `page_bookmarks`.
- `library_chapters`: known chapters, to detect new ones.
- `downloads`, `download_queue`.
- `offline_title_cache`: the last loaded copy of each title.
- `tracker_links`, `source_packs`.
- `title_sources`: linked, suggested and dismissed other sources of a title.
- `glossary_terms`: the per-title glossary of chapter translation. `status` is `confirmed` (the user's), `suggested` (proposed by AI translation and already used) or `rejected` (kept only so it isn't proposed again; the DAO's glossary queries leave it out); `kind` is a codex kind when AI translation named one. `title_prefs.translateStyle` holds the title's style note for AI translation.
- AI translation (`feature/novel/llm/` and `translate/LlmTranslation.kt`): `LlmClient` calls any OpenAI-compatible `/chat/completions` (presets for OpenRouter, OpenAI, DeepSeek, Gemini, or a typed URL) with the user's key from the encrypted private store (`translate_llm_<service>_secret`). `LlmProvider` is a `TranslationProvider` that sends, with each part of about 10,000 characters, only the glossary items that part contains (its `data-g` marks, plus names found earlier in the session), the last translated paragraphs and the style note, and asks for JSON with the paragraphs by id and the new names it met. Those pass `filterSuggestions`, which takes each in its base form (`llm/NameKey.kt`: `nameKey` drops possessives and hyphenated or attached Japanese and Korean honorifics, so "Yu San A-san" is "Yu San A"), and are stored as `suggested` terms; `tidyGlossary` drops older suggestions that repeat a name. `ChapterTranslator` keeps the parts that came back in a `.part.json` file, so a failed chapter only asks for the rest. `LlmClient` caps each answer (`max_tokens`, `max_completion_tokens` for OpenAI), asks for the least reasoning the service allows, leaves out a parameter for the rest of the run once the service names it in a 400, retries 429 and 502–504 after `Retry-After`, and reports each answer's `usage` to `AiUsage` (monthly totals per feature in the private store, shown in Settings → AI and translation). `AiQueue` runs a title's digest jobs one after another, at most two AI jobs at a time. Its cache key leaves the glossary out (`glossaryInCacheKey = false`), so a growing glossary doesn't throw cached chapters away; cached HTML keeps glossary marks by source (`data-gs`) instead of by number. The same client writes the AI recap.
- `chapter_digests`: AI digests of finished chapters, one per chapter and language, made from that chapter's text only (`data/story/AiRecap.kt`). `AiRecap.recap` joins the last four digests before the spot (making the missing ones together, up to 40,000 characters of text per request) with the current chapter up to the spot for "Previously…". A digest in any language serves; the summary is written in the wanted one. The summary is kept in `cacheDir/ai-recap`, keyed by the chapter, the spot in steps of ten paragraphs, the language, the model and the digests used. A codex summary sends the digests that name the entry plus the last four, not all of them. In backups since format 9, not in sync; cleared with the story index.
- AI story notes (`data/story/StoryNotes.kt`, setting `story_notes_mode`: `off`, `finished`, `needed`; before v1.12.0 the `story_ai_recap` switch, on = `finished`): one request per chapter brings `ChapterNotes` (a summary, up to 8 names with variants, kind and a fact, up to 5 relations). With AI translation, `LlmProvider` asks for them in each part's answer (`notes`); `ReaderTranslation.saveNotes` joins the parts in chapter order and stores them, with the summary as the digest only when the parts cover most of the chapter. Otherwise the story pass (`AiRecap.digests`, the digest request) asks for them, several chapters per request: on finishing a chapter and for up to 10 earlier ones on opening a title (`finished`), or when the recap or a summary needs them (`needed`). `StoryNotes.save` matches names by `nameKey` against every codex name and alias: new ones become entries with `origin = ai` and `firstChapterId`, known ones get new spellings, `ignored` ones (AI entries the reader removed) are skipped. Facts go to `codex_facts` (one per `nameKey` and chapter), relations to `codex_relations` with origin `notes:…`, once per name pair and label.
- Codex summaries and `codex_relations` (`data/story/StoryKnowledge.kt`): "Summarize with AI" in the lookup sheet sends the entry and its facts before the spot (origin `facts:…`, kept up to the last fact's chapter; asking again without newer facts makes no request). Without facts it sends the entry, the other codex names, the digests of chapters before the spot and the entry's mentions before it; that answer also replaces the relations the summary wrote (`llm:…`), not those from notes. A relation is stored by name (`fromName`, `toName`, `label` = what `toName` is to `fromName`) with `upToChapterId`. Spoiler rules: `visibleAt` (at or before the reader's chapter) for summaries and relations written at the spot; `visibleBefore` (strictly before) for facts, facts summaries and notes relations, which cover whole chapters; `entryVisible` hides an `ai` entry before its `firstChapterId` in lookup and on the codex screen (which takes the spot from the title's progress and its cached chapter list). In backups since format 9.
- `chapter_text` (plain text of read and downloaded novel chapters, one row per block, original language) with its full-text index `chapter_text_fts` (FTS4 over that table, `unicode61` tokenizer, kept in sync by triggers). Written by `StoryIndex` (`feature/novel/data/story/`) when a chapter is loaded and by a backfill of downloaded chapters on a lookup; searched by `StoryLookup` for the reader's name lookup and read by the "Previously…" recap. Rebuildable, so it is not in backups.
- `codex_entries` and `codex_aliases`: names of a title (kind, note, other spellings), saved by the reader (`origin = user`) or added by story notes (`ai`; `ignored` once the reader removes one, left out of `codexFor`/`codexOnce`). Unique per title and name; aliases go with their entry. Saving an entry makes it the reader's, and renaming moves its facts. `summary`, `summaryOrigin` and `summaryUpToChapterId` hold the AI summary. In backups (format 7; origin, first chapter and summary since 9), not yet in sync.
- `codex_facts`: what one chapter showed about a name, keyed by `nameKey` and chapter (database 23).

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

Roborazzi (on the Robolectric setup) renders Home, Library (grid, list, empty), Title details, Chapter list, Settings and the Reader bars in light, dark and large font (font scale 2.0), from fixed data with no network. They live in `core/ui/src/test/java/com/lume/app/ui/screenshots/` (the Reader bars in `feature/comics/src/test/java/com/lume/app/ui/screenshots/` and the novel reader and its settings sheet in `feature/novel/`, each with its own copy of `ScreenshotTest`) and use the stateless composables of each screen (`HomeFeedList`, `LibraryContent`, `TitleHeader`, `ChapterRow`, `SettingsSections`, `ReaderTopBar`/`ReaderBottomBar`). The golden images are in each module's `src/test/screenshots/`.

- A plain `testDebugUnitTest` only composes the screens; it does not compare images.
- `./gradlew :core:ui:verifyRoborazziDebug :feature:comics:verifyRoborazziDebug` compares against the goldens (up to 1% of pixels may differ, to absorb text anti-aliasing between operating systems). CI runs it on Linux; on failure it uploads the images as drawn on CI as an artifact.
- After a deliberate UI change, re-record and commit the images: `./gradlew :core:ui:recordRoborazziDebug :feature:comics:recordRoborazziDebug --tests "com.lume.app.ui.screenshots.*"`, then look at the changed PNGs in the diff.
- New screens: add a test class that extends `ScreenshotTest` and calls `shoot("name", Look.Light) { ... }` for each look.
