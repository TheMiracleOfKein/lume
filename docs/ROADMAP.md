# Lume roadmap

This roadmap lists what comes next for Lume, the bigger features that set it apart, the known problems, and what has shipped.

Effort is rough: **S** = under a day, **M** = a few days, **L** = a week or more.

Status:
- ✅ done
- 🟡 partly done (the note says what is left)
- ⏸️ on hold (the note says why)
- No mark: planned

## Principles

These hold for every release:

- **Everything stays on the phone.** No Lume account, no Lume server, no ads, no analytics. Network access goes only to the sources, trackers and services the user turned on ([PRIVACY.md](../PRIVACY.md)).
- **Sources are first-party engines.** No extension APKs ([why](MIHON_EXTENSIONS.md)); site families are added as JSON packs.
- **Nothing is lost on update.** Database changes are additive migrations with tests; backups from older versions keep loading.
- **English and Russian for every string.**
- **Checked on a device before a release.** Each release adds its items to [DEVICE_TESTING.md](DEVICE_TESTING.md).

## At a glance

| Version | Theme | Highlights | Size |
| --- | --- | --- | --- |
| 0.6.x | Maintenance | Clearer settings with search, reading lists, similar titles, source filters and browse sections; device testing, source fixes | S–M |
| **0.7** | Sync and everyday reading 🟡 | Sync between devices; page slider, orientation lock, auto-scroll, colour filters | L |
| **0.8** | Reader 2.0 | Tall pages without memory problems, sharp zoom, crop and spreads, guided panel view, Coil 3 | L |
| **0.9** | Library and sources | Self-hosted servers (OPDS, Komga, Kavita), CBR and PDF, watched folders, smarter updates, statistics | L |
| **0.10** | On-device AI | Translate raws, sharper old scans (separate build flavor) | L |
| **1.0** | Stable | Distribution, update check, crash log, accessibility, encrypted sign-ins, test coverage, user guide | M–L |

Order can change when a source breaks or a bug needs a patch release first.

## v0.6.x: maintenance

Patch releases from the v0.6 line, as needed.

- 🟡 **Device testing** of v0.3–v0.6.1 on real phones: [DEVICE_TESTING.md](DEVICE_TESTING.md). Fix what it finds (M).
- ✅ **Settings back in Android cloud backup** (v0.6.2). Tracker tokens moved to their own store (`lume_private`) on the first start; cloud backup includes the settings again and leaves the sign-ins out.
- **Real tracker accounts** for MyAnimeList, Kitsu and MangaUpdates: sign in, update progress, edit status (S each).
- **Startup numbers** from `StartupBenchmark` on a real phone, written down as the baseline for later releases (S).
- **Source fixes** from the weekly live check ([live-sources.yml](../.github/workflows/live-sources.yml)).

**v0.6.2: clearer settings** ✅ released 2026-10-20 ([details](#v062-clearer-settings--released-2026-10-20))
- ✅ Settings in sections, explanations, search in settings; `SettingsScreen.kt` split.

**v0.6.3: reading lists** ✅ released 2026-10-20 ([details](#v063-reading-lists--released-2026-10-20))
- ✅ Share or save a reading list from the library; open one shared to Lume; `LibraryScreen.kt` split.

**v0.6.4: similar titles** ✅ released 2026-10-20 ([details](#v064-similar-titles--released-2026-10-20))
- ✅ Recommendations on the title screen ("Similar titles") from AniList or MyAnimeList; a tap searches your sources for that title.
- ⏸️ Split `Database.kt`: the release didn't touch it, so it moves on (next release that changes the database).

**v0.6.5: polish from emulator testing** ✅ released 2026-10-20 ([details](#v065-polish-from-emulator-testing--released-2026-10-20))
- ✅ Hint text no longer collides with chip rows in Downloads, Backup and Network settings.
- ✅ The reader's chapter line is readable over light pages.
- ✅ "Import pack from URL" with an empty field now says what is missing.
- ⏸️ Drawer **Theme** button: the code toggles correctly; the drawer staying open looks intended. Not changed.

**v0.6.6: source filters and browse sections** ✅ released 2026-10-20 ([details](#v066-source-filters-and-browse-sections--released-2026-10-20))
- ✅ The site's own genres, tags, types and statuses in the filter panel, with include/exclude; filters together with search text where the site allows it.
- ✅ Browse section chips in the catalogue; author, artist, year and score on the title screen.
- ⏸️ MangaBuff and MangaKatana filters; Lib site descriptions (separate fix).

## v0.7: sync and everyday reading

**Goal:** read on the phone, continue on the tablet, without an account.

**v0.7.0: sync and reader controls** ✅ released 2026-10-21 ([details](#v070-sync-and-reader-controls--released-2026-10-21))

**Sync between devices** (signature feature 3, L) 🟡
- ✅ Library, categories, reading progress (the exact spot per chapter), read marks, page bookmarks, linked sources and tracker links sync, plus per-title reader settings and source packs. Sign-ins, downloads and app settings never sync.
- Backends:
  - ✅ **WebDAV** (Nextcloud and others);
  - ✅ **a shared folder** picked with the file picker (works with Syncthing);
  - ⏸️ **Google Drive**: needs an OAuth client; a cloud drive app that syncs a folder works through "Folder" meanwhile.
- ✅ Each device writes its own change file, so a shared folder never has write conflicts. Lume merges the files.
  - The newest change wins.
  - For reading progress, the furthest position wins unless you reset it; "Continue" goes to the title read last.
- ✅ "Sync now", sync in the background (WorkManager) and at start, and a status line with the last sync and any error.
- ✅ Optional passphrase that encrypts the sync files (AES-256-GCM).
- 🟡 Done when: two emulators and one phone stay in sync through Nextcloud and through a Syncthing folder, including offline edits on both sides. The merge rules have unit tests; the two-device runs are in [DEVICE_TESTING.md](DEVICE_TESTING.md) and not done yet.

**Reader quick wins** ✅
- ✅ Page slider (S).
- ✅ Orientation lock (S).
- ✅ Webtoon auto-scroll with speed control; tap zones in webtoon mode (S).
- ✅ Colour filters, e-ink mode (no animations, high contrast), custom brightness (S).
- ✅ Save or share a page with a link to its source (S).

**Foundation**
- ⏸️ Tests for ViewModels of the reader and the library (M): moved to the next v0.7.x release; v0.7.0 adds tests for the sync rules instead.

**v0.7.1: subscribed downloads** ✅ released 2026-10-26 ([details](#v071-subscribed-downloads--released-2026-10-26))
- ✅ A bell on a library title subscribes it to new-chapter downloads; "Download new chapters" becomes Off / All titles / Subscribed.
- ✅ AGP back to 9.2.1 so the installed Android Studio syncs the project; Gradle 9.8.2 and library updates.
- ⏸️ Subscribing several titles at once from the library selection.
- ⏸️ ViewModel tests: still for a later v0.7.x release.

**v0.7.2: MangaLib bookmark import** ✅ released 2026-10-31 ([details](#v072-mangalib-bookmark-import--released-2026-10-31))
- ✅ Signed in to MangaLib, HentaiLib or YaoiLib: ⋮ → Import bookmarks adds the account's bookmarks to the library, folders as categories.
- ⏸️ Read progress from the site: the API's last-read field couldn't be checked from where this was built (the API is region-blocked here).
- ⏸️ ViewModel tests: still for a later v0.7.x release.

**v0.7.3: bookmark import fix** ✅ released 2026-10-31 ([details](#v073-bookmark-import-fix--released-2026-10-31))
- ✅ Import bookmarks no longer fails with "Not a Lib source" (the registry's wrappers hid the engine).
- ✅ All bookmarks in one `status=0` pass, so the user's own folders are imported too; built-in folders are named even when the folder list fails.
- ⏸️ Read progress from the site: the `status=0` answer has `item` (last chapter read) and `progress`, still to be mapped onto chapters.

**v0.7.4: read progress with bookmark import** ✅ released 2026-10-31 ([details](#v074-read-progress-with-bookmark-import--released-2026-10-31))
- ✅ The last chapter read on the site and every earlier chapter (all teams) are marked read; titles without local progress get a continue point.
- ⏸️ The bookmark's `progress` field isn't used: in real answers it doesn't line up with the chapter in `item`.

**v0.7.5: import progress and app updates** ✅ released 2026-11-01 ([details](#v075-import-progress-and-app-updates--released-2026-11-01))
- ✅ Bookmark import shows its step and a progress bar, and keeps running after leaving Sources.
- ✅ Update check against GitHub releases, with download and install.
- ⏸️ The import doesn't survive Android killing the process (no WorkManager job yet).

**v0.7.6: request limit, bell pool and library marks** ✅ released 2026-11-01 ([details](#v076-request-limit-bell-pool-and-library-marks--released-2026-11-01))
- ✅ Lib API requests spaced to one per 0.5 s; import brings only Reading / Planned / Dropped / Read, and "Read" marks every chapter.
- ✅ Update checks look only at titles with the bell (up to 20), or the 20 last read when none has it.
- ✅ Library badge shows read of total; search results in the library get a status colour bar.
- ⏸️ Search marks don't match a library title saved from another source.

**v0.7.8: login, back and a fast library** ✅ released 2026-11-01 ([details](#v078-login-back-and-a-fast-library--released-2026-11-01))
- ✅ The Lib login screen ignores a site session left over from before sign-out and clears it, so the login form shows.
- ✅ Going back is instant on every screen; the back animation is gone.
- ✅ A big library (600+ titles) opens at once instead of after about 10 seconds.

**v0.7.9: no back animation anywhere** ✅ released 2026-11-01 ([details](#v079-no-back-animation-anywhere--released-2026-11-01))
- ✅ Android's own predictive-back animations (back-to-home card, login screens, bottom sheets) are off; the screen stays still during the back gesture.

**v0.7.10: safety** ✅ released 2026-11-01 ([details](#v0710-safety--released-2026-11-01))
- ✅ Downloads save to a temporary folder and count only when complete; leftovers from interrupted downloads are removed on start.
- ✅ Backup restore runs in one transaction; backups, sync files and pages have size limits.
- ✅ Source packs can't use a built-in source's id; download folder names can't leave their folder.
- ✅ App updates come only from Lume's GitHub releases and must match their size and checksum.
- ✅ One tracker token refresh at a time; a server error no longer signs out.
- ✅ The app lock hides content from accessibility services.
- ⏸️ Room keeps wiping the database on a downgrade: Android refuses to install an older APK over a newer one, so this happens only with `adb install -d`; removing it would crash instead.
- ⏸️ OAuth `state` checks: the redirect goes to Lume's own link scheme and MyAnimeList already uses PKCE; planned with the tracker rework.

**v0.7.11: reader and navigation** ✅ released 2026-11-01 ([details](#v0711-reader-and-navigation--released-2026-11-01))
- ✅ Very tall webtoon pages (Asura's 800×~14,500 strips) draw as stacked slices instead of staying black.
- ✅ Links from notifications, the widget, shortcuts and shared links open on top of the current screens; the menu no longer stacks duplicates.
- ✅ The reader shows Back and the title while loading or failed; prev/next follow reading order, are greyed out at the ends, and pulling past the end says so.
- ✅ Per-page spinner; one shared zoom gesture; every back arrow is labelled "Back".
- ⏸️ Links still open under the app lock (hidden by it); waiting for unlock needs the lock state in the shell.

**v0.7.7: Lib sign-out and back animation** ✅ released 2026-11-01 ([details](#v077-lib-sign-out-and-back-animation--released-2026-11-01))
- ✅ Sign-out of a Lib site clears its web session, so another account can log in; the login screen offers saved passwords through autofill.
- ✅ Going back shrinks the leaving screen into a rounded card with a shadow (the rounded corners left over from v0.3).
- ⏸️ "Sign in with Google" inside the login screen: Google blocks it in web views.

**v0.7.x: send downloads to a nearby device** (L)

Sync moves your library and progress, never the downloaded pages. This moves the pages: pick downloaded chapters on one phone, and another phone nearby receives them, without the internet and without a server.
- **Choosing.** In Downloads (or on a title's chapter list), select titles or chapters → "Send to a nearby device". The sender shows the total size.
- **Connecting**, fastest first, with nothing from Google Play services (so F-Droid builds keep it):
  - **Same Wi-Fi:** the receiver finds the sender by itself (Android's network service discovery, `_lume._tcp`); a QR code with the address works when discovery is blocked (guest or office networks).
  - **No shared Wi-Fi:** Wi-Fi Direct (`WifiP2pManager`, built into Android), or the sender's local-only hotspot, joined by scanning its QR code.
  - Both run at Wi-Fi speed (usually 10–40 MB/s), far faster than Bluetooth, which stays out.
- **Accepting.** The receiver sees who is sending, the titles, chapters and size, and accepts or declines. Nothing is written before that.
- **Safety.** A short code shown on both screens, or the QR code, pairs the two phones; the transfer is encrypted with a key made for that session only. Lume listens only while the send screen is open.
- **Transfer.** Chapters go one by one as CBZ with their metadata (title, source, chapter names and numbers), with a checksum each; a dropped connection resumes at the chapter it stopped on. The receiver adds the title to the library if it isn't there, and the chapters show as downloaded. Read marks can come along (a switch).
- Done when: 1 GB of chapters moves between two phones on the same Wi-Fi and over Wi-Fi Direct, a cancelled transfer resumes, and declining writes nothing.

## v0.8: reader 2.0

**Goal:** any page, any size, any phone: no memory crashes, sharp zoom, layouts that fit the screen.

**Image pipeline**
- **Coil 3** instead of Coil 2 (M). Coil 2 only gets fixes; Coil 3 brings better memory handling and the API the next items need.
- **Tall pages in pieces** (M). Very tall webtoon images are decoded in tiles, so they load faster and don't run out of memory on older phones.
- **Choose how many pages load ahead** (S).
- **Sharp zoom** (M). The visible part is decoded again at the zoomed size.

**Layouts**
- **Crop white borders; split wide pages; two-page spreads** in landscape and on tablets (M).
- **Previous chapter above** the current one in seamless mode (M).
- **Two-pane title screen on tablets**: details on the left, chapters on the right (M).

**Guided panel view** (signature feature 2, first part, L)
- Classic image processing finds the panels of a page; no model, no download.
- A tap moves to the next panel with a smooth zoom, in the page's reading direction.
- The same detection powers border cropping and spread splitting.
- Unblocks **"See exactly where you stopped"**: "Continue" cards and page bookmarks show the panel you were reading.

**Performance**
- A frame-time benchmark for library and reader scrolling next to `StartupBenchmark`; the profile is recorded again (S).
- ⏸️ **Rounded screen corners during the back gesture**: added in v0.7.7, removed again in v0.7.8 (it looked different from screen to screen); going back is instant.

## v0.9: library and sources

**Goal:** bring every comic you have into one place, and spend less battery and data on updates.

**Self-hosted servers** (L)
- An **OPDS** engine: Komga, Kavita, Calibre-Web and other servers that speak OPDS, with sign-in.
- Native **Komga** and **Kavita** engines where OPDS is too limited (read progress back to the server).

**Local files**
- **CBR, CB7 and PDF** (M). PDF through Android's own renderer.
- **Watched folders** (M). New chapters in an imported folder appear without importing again.
- **Any folder as download storage** (M). The reader reads pages through the Storage Access Framework instead of file paths.

**Library**
- **Smarter updates** (M): skip completed titles, titles you haven't started or titles with many unread chapters; update only some categories; only while charging.
- **Statistics** (M): chapters and pages read, reading time, per title and per month. Only on the phone.
- **Grouped notifications** per title, and a switch per category (S).
- **Import a tracker list** (M): your AniList or MyAnimeList "Reading" list becomes library entries, found on your sources with the same matching as "Found for you".
- **Import Kotatsu backups** (M).

**Sources**
- InkStory and Com-X.life ([ENGINES.md](ENGINES.md)) (M each).
- **Offline parser tests** with saved pages for every engine (M), so a parser change is checked without the live sites.
- **Source status in the app** (S): the last error per source on the Sources screen, so a broken source is visible before you open a title.

## v0.10: on-device AI

Signature feature 2, second part. It ships in a separate build flavor (`ai`), so the standard build stays fully open source and small.

- **Translate raws** (L). Text recognition (Japanese, Korean, Chinese, Latin scripts) and on-device translation. Lume finds each speech bubble, clears it, and draws the translated text inside.
  - Results are saved per page.
  - It can be turned on per title.
  - It works offline once the language is downloaded.
- **Sharper old scans** (L). Optional upscaling of low-resolution pages, piece by piece on the GPU, cached.
- **Learned panel detection** (M), optional, for pages where the classic detection from v0.8 fails.
- Done when: a 20-page raw chapter translates on a mid-range phone in under a minute, offline, and the `ai` build stays under 60 MB without models.

Learned models stay optional because of their size, their speed on older phones and the licenses of their training data.

## v1.0: stable

**Goal:** an app people can install from a store, trust with their library, and get help with.

**Distribution**
- **IzzyOnDroid** first; **F-Droid** when the build is reproducible (the standard flavor has no proprietary parts) (M).
- **Obtainium**: release names and assets stay stable; a checksum file next to each APK (S).
- **Update check** (S), off by default: asks GitHub's release API for a newer version, nothing else.
- Google Play stays out ([below](#out-of-scope)).

**Trust**
- **Encrypted sign-ins** (M): tracker and site tokens encrypted with the Android Keystore.
- **Crash log** (S): crashes are saved on the phone; About → "Share log" attaches it to a bug report. Nothing is sent automatically.
- **Backup format documented** and kept stable; every older backup keeps loading (S).

**Everyone**
- **Accessibility pass** (M): TalkBack labels for every button, font size up to 200 %, touch targets, contrast.
- **Translations** through a translation platform (for example Weblate), starting with Ukrainian, Spanish and Brazilian Portuguese (M, then contributors).
- **User guide** (M): reading, sources and packs, sync, backups, trackers, troubleshooting.

**Quality**
- Emulator tests on CI (Gradle managed devices): migrations and a smoke test of the main screens (M).
- Screenshot tests for the main screens in light, dark and large font (M).
- Optional release workflow that builds and signs from a tag with repository secrets (S), so a release no longer depends on one computer.
- Done when: the device checklist passes on three phones and one tablet, no open crash reports, and the user guide covers every setting.

## Every release

- Source fixes from the weekly live check; new packs in the [catalogue](packs/README.md).
- The device checklist for what changed.
- Dependabot updates; lint stays at zero issues; the baseline profile is recorded again when screens change.
- Release notes in `docs/releases/`, roadmap marks, and the version in `app/build.gradle.kts`.

## Known problems and technical debt

| Problem | Plan |
| --- | --- |
| ✅ Settings are left out of Android cloud backup (tokens share their file) | Fixed in v0.6.2: tokens have their own store |
| MyAnimeList, Kitsu and MangaUpdates never tried with real accounts | v0.6.x |
| MangaRead and ToonGod often show Cloudflare checks; ReManga and MangaBuff block some regions | The in-app browser handles checks; region blocks can't be fixed in the app |
| HentaiLib needs a signed-in account to read | Works as designed since v0.6.1; the reader says so |
| Room 6→7 migration can't be tested (no v6 schema was exported) | Accepted; every later migration is tested |
| The Room migration test is skipped on Windows JVM runs | CI (Linux) runs it; v1.0 adds emulator tests on CI |
| Coil 2 is in maintenance | v0.8: Coil 3 |
| Large files: `Database.kt` (about 770 lines); 🟡 `SettingsScreen.kt` and `LibraryScreen.kt` split in v0.6.2 and v0.6.3 | `Database.kt`: with the next database change |
| Releases are built on one computer | v1.0: optional signed release workflow |
| Engine parsers are only tested against the live sites | v0.9: offline tests with saved pages |

## Ideas, not planned yet

- Android TV and Chromebook: D-pad and keyboard navigation in the reader.
- A community pack catalogue with its own review rules.

## Out of scope

- Loading Mihon / Tachiyomi extension APKs or any downloaded code ([MIHON_EXTENSIONS.md](MIHON_EXTENSIONS.md)).
- A Lume account or server, ads, analytics, telemetry.
- Hosting or mirroring content.
- Google Play: its policies on third-party content don't fit a reader with built-in sources.

## Signature features

### 1. One title, many sources ✅ done in v0.6

A library entry can be backed by several sources at once.

- **Merged chapter list.** Chapters from all linked sources are combined by volume and number. Each chapter comes from the best available source and shows a small source badge.
- **Found for you.** When you add a title, Lume quietly searches your other sources and suggests "Also on X, Y". It matches titles and alternative titles.
- **Never stuck.** If a chapter fails on one source (Cloudflare, region block, removed), Lume loads it from the next one. Your position carries over, down to the spot inside the page.
- **First to publish.** Update notifications tell you which source got the new chapter first.
- **One-tap migration.** Move progress, page bookmarks, categories and tracker links to another source.
- ✅ Library badge with the number of sources (v0.6.1).

### 2. On-device reading help (v0.8 and v0.10)

Everything runs on the phone, with no account.

- **Guided panel view** (v0.8): classic panel detection, no model.
- **Translate raws** and **sharper old scans** (v0.10): in the separate `ai` build flavor.

### 3. Sync and continuity 🟡

- ✅ **Home-screen widget** and **"Continue <title>" shortcuts** (v0.6).
- ✅ **Sync between devices** (v0.7.0): a folder or WebDAV, optional passphrase. Google Drive waits.
- ⏸️ **See exactly where you stopped** (v0.8): "Continue" cards and page bookmarks show the panel you were reading. Waits for panel detection; today page bookmarks show a crop of the page.

## Released

- **v0.1.0** (2026-10-03): first public release. [Notes](releases/v0.1.0.md).
- **v0.2.0** (2026-10-12): faster global search, fullscreen reading, source fixes. [Notes](releases/v0.2.0.md).

### v0.3: exact resume and smooth motion ✅ released 2026-10-17

Release notes: [docs/releases/v0.3.0.md](releases/v0.3.0.md).

- ✅ **Exact resume inside a chapter.** Lume remembers where you are *inside* a page, not just which page, separately for every chapter. Reopening lands on the same spot:
  - in very tall webtoon pages;
  - after you rotate the phone;
  - after Android closes the app;
  - after reading on into the next chapter.
- ✅ **Page bookmarks.** Pin any spot with an optional note. Bookmarks appear on the title screen and jump back to the exact spot.
- ✅ **A chapter counts as read when you finish it**, not when you open it. Trackers update at the same moment.
- ✅ **Smoother app.**
  - No jumps when the bottom bar hides or shows.
  - The reader does not resize when its controls hide.
  - Screens keep their content when you go back.
  - Settings opens without switches animating.
  - The side menu follows a swipe.
  - Back transitions follow your finger.
- ✅ **Fixes.**
  - New chapters always appear on the title screen.
  - Update notifications ask for permission and open the library.
  - Background updates no longer miss sources.
  - Interrupted downloads resume, and failed ones can be retried.
  - "Clear" on Home asks first.
- ✅ **Smaller, faster release build.** R8 shrinking cut the APK from about 16 MB to 3 MB. A baseline profile speeds up startup.

**Left over from v0.3**
- ⏸️ **Rounded screen corners during the back gesture.** Removed in v0.3, back in v0.7.7, removed again in v0.7.8: going back is now instant everywhere.
- ✅ **Measured baseline profile.** Done in v0.4: the `:baselineprofile` module records it on an emulator.
- 🟡 **Device testing of v0.3.** v0.3 was released after builds and unit tests only. The device checklist (resume, motion, notifications, R8 build) is still to be worked through on real phones; it is in [DEVICE_TESTING.md](DEVICE_TESTING.md).

### v0.4: foundation, library and chapters ✅ released 2026-10-17

Release notes: [docs/releases/v0.4.0.md](releases/v0.4.0.md).

**Foundation**
- ✅ ViewModels for every screen. Library, Updates, History, Downloads, Settings, Sources and Categories got theirs in v0.4.
- ✅ A small app container (`AppContainer`) instead of `LumeApp.instance` lookups. Only `appString()` still uses `LumeApp.instance`.
- 🟡 Room migration tests. 7→8 is tested on Robolectric (CI) and on a device. Left: the test is skipped on Windows JVM runs, because Room's test helper compares paths with "/". The 6→7 migration can only be checked by hand, because no v6 schema was ever exported.
- ✅ GitHub Actions with build, unit tests and lint.
- ✅ Tests for reading order and tracker matching, plus the new library, chapter-list and update logic.
- ✅ Measured baseline profile and a startup benchmark (`:baselineprofile`).

**Library**
- ✅ Grid, compact and list modes; sorting; filters (unread, downloaded, started, tracking, completed); search in the library.
- ✅ Unread and downloaded counts on each title.
- ✅ Select several titles for bulk actions (categories, mark read/unread, download unread, remove), with range select.
- ✅ Rename, delete and reorder categories; default category for new titles.

**Updates**
- ✅ An Updates screen with new chapters from the whole library. History moved to the menu.
- ✅ "Update now" and pull to refresh on the Updates screen.
- ✅ New chapters are detected by id instead of by count.

**Chapters**
- ✅ Mark read / unread, "mark previous as read", filters (unread, read, downloaded, with page bookmarks).
- ✅ Teams on MangaLib, YaoiLib and HentaiLib: one copy per chapter by default, team chips, "show all copies".
- ✅ "Download next N / all unread", delete downloaded chapters, range select.

**Left over from v0.4**
- 🟡 **Device testing of v0.4.** Checked on an emulator, including the signed R8 release APK: the app starts, the migration runs, the library grid and its counts, chapter selection, the reader, and "Update now". The rest still needs real phones: [DEVICE_TESTING.md](DEVICE_TESTING.md).
- **Startup numbers.** `StartupBenchmark` exists but has not been run yet; run it on a real phone to measure the profile's gain (S).

### v0.5: sources, downloads, backups, trackers ✅ released 2026-10-17

Release notes: [docs/releases/v0.5.0.md](releases/v0.5.0.md).

**Sources**
- ✅ Filter panel for catalogue search. MangaDex: sort, language, status, audience, content rating. Madara: sort, status, adult content. Other engines: labelled sort options.
- ✅ More results pages in search; search history.
- ✅ Per-source settings: MangaDex chapter languages, content rating and data saver.
- ✅ Browse any source without changing the active one; enable, disable and pin sources.
- ✅ Open a site link shared from the browser directly in Lume (bundled hosts open directly; any link can be shared to Lume).
- ✅ A pack index with update checks (`docs/packs/index.json`).

**Downloads**
- ✅ Downloads run as a background job with a notification; pause, resume, reorder and retry.
- ✅ Several downloads at once (one chapter per source, sources in parallel, 1–4 pages at once); downloaded files are checked to be images.
- 🟡 Save as CBZ; choose the storage folder. CBZ is done. Storage is a choice between internal storage, external storage and SD cards; any folder from the file picker is left, because the reader needs plain file paths.
- ✅ Download new chapters automatically; delete chapters after reading; storage usage.

**Local files**
- 🟡 Import a folder from your device; ComicInfo.xml and covers; clean temporary files. Done as a copy into the local library; the picked folder is not watched for later changes.

**Backups**
- ✅ Save and open backups anywhere on the device; automatic backups with rotation; include settings (account sign-ins stay out).
- ✅ **Import Mihon / Tachiyomi backups (`.tachibk`)**: titles of sources Lume has, categories, tracker links, read chapters (matched by chapter number) and the last chapter read.

**Trackers**
- ✅ Shikimori stays signed in (token refresh).
- ✅ Edit status, score and dates; link automatically when adding to the library (exact title match only).
- 🟡 MyAnimeList, Kitsu, MangaUpdates. All three are built, and the v0.5.0 release carries the MyAnimeList client ID (its sign-in page opens). None of the three has been tried with a real account yet.

**App**
- ✅ Follow the system theme, Material You colors, pure-black AMOLED theme.
- ✅ Navigation rail on tablets (screens 600 dp and wider).
- ✅ About and licenses page; clear cache and cookies; DNS over HTTPS.
- ✅ Incognito mode, app lock, hide content in the recent-apps screen.
- ✅ Translate chapter labels that sources send only in Russian.

**Left over from v0.5**
- 🟡 **Device testing of v0.5.** Checked on an emulator: search with filters only and paging, a shared MangaDex link, the Sources, Settings and Downloads screens, with no crashes. Tracker sign-ins, Mihon import with a real backup, the download notification, CBZ, local import, app lock and the tablet rail still need real devices and accounts: [DEVICE_TESTING.md](DEVICE_TESTING.md).

### v0.6: one title, many sources ✅ released 2026-10-19

Release notes: [docs/releases/v0.6.0.md](releases/v0.6.0.md).

**One title, many sources** (signature feature 1)
- ✅ **Merged chapter list.** Linked sources add their chapters to the title; each chapter comes from the main source when it has it, else from the first linked source that does, with a source badge. "Show all copies" lists every source's copy. Linked chapters without a number can't be placed and are left out.
- ✅ **Found for you.** Adding a title to the library searches the other enabled sources by every name of the title (MangaDex gives all its alternative names) and suggests the sources that have a title with the same name ("Also on …"). Close names (spin-offs, side stories) are never suggested on their own; "Find on other sources" lists them as "maybe" to link by hand.
- ✅ **Never stuck.** A chapter that fails on its source (site check, region block, removed) loads from another source's copy, in the reader and in downloads. The chapter keeps its place, so the spot inside the page and the read mark stay.
- ✅ **First to publish.** Library checks fetch every linked source; the copy found first is the new chapter. Notifications and the Updates screen name the source that had it first.
- ✅ **One-tap migration.** "Make main" moves the title to a linked source with progress, per-chapter spots, read marks, page bookmarks, categories, tracker links, chapter list settings and downloads; the old source stays linked. "Move here" also unlinks the old source. Unlinking moves read marks, spots, bookmarks and downloads to another source's copy where there is one.

**Continuity** (from signature feature 3)
- ✅ **Home-screen widget** with the last three titles; a tap opens the reader where you stopped.
- ✅ **App shortcuts** "Continue <title>" for the last three titles.
- Both show nothing with app lock or "hide in recent apps" on.

**Left over from v0.6**
- 🟡 **Device testing of v0.6.** Checked on an emulator with real sources: the 8→9 migration test, finding Solo Leveling on other sources from MangaDex (which has no chapters), linking (0 → 207 chapters with source badges), reading a linked chapter, unlink, make main (library entry, Continue and links moved), the Continue shortcut and the widget. That run suggested two spin-offs too; the rule was made stricter afterwards (unit-tested, not re-run on the emulator). Not checked: the fallback with a source that really fails, downloads of linked chapters, the update notification naming the first source, and real phones: [DEVICE_TESTING.md](DEVICE_TESTING.md).
- ✅ **Library badge for titles with several sources.** Shipped in v0.6.1.

### v0.6.1: small fixes and repository health ✅ released 2026-10-19

Release notes: [docs/releases/v0.6.1.md](releases/v0.6.1.md).

- ✅ **Library badge** with the number of sources of a title (can be turned off in the library display settings).
- ✅ **Icons.** A proper adaptive launcher icon with a themed (monochrome) layer; notifications show the Lume mark instead of a white square.
- ✅ **Android backup.** Cloud backup keeps the library database but leaves out sign-ins, cookies, downloads and local files (large files stopped cloud backups entirely).
- ✅ **Russian plurals** in the "new chapters" notification and "Download next N".
- ✅ **YaoiLib pages load again.** Its main image server refuses requests; LibSocial sources now try the site's other image servers and remember the one that works.
- ✅ **HentaiLib** says that its chapters need a signed-in account, instead of "HTTP 404".
- ✅ **Lint clean** (36 warnings → 0); patch updates of AGP, Kotlin, Gradle and test libraries.
- ✅ **Repository.** README, contributing guide, architecture overview, privacy and security policies, issue and PR templates, device testing checklist.
- ✅ **CI.** The R8 release build runs on every PR; a weekly workflow checks every bundled source and catalogue pack against the live sites; Dependabot for Gradle and GitHub Actions.

What was still open moved into [v0.6.x](#v06x-maintenance) and later versions.

### v0.6.2: clearer settings ✅ released 2026-10-20

Release notes: [docs/releases/v0.6.2.md](releases/v0.6.2.md).

- ✅ **Settings in sections.** The main settings page lists sections (Appearance, Reader, Library and updates, Downloads, Sources and search, Trackers, Backup and restore, Privacy, Network and storage, About); each opens its own page and shows what it holds.
- ✅ **Explanations.** Settings whose effect isn't obvious from the name got a short line under it (reader controls, Wi-Fi only, parallel pages, clear cache and cookies, backups and more).
- ✅ **Search in settings.** One field finds a setting by its name, its section, its explanation or related words ("oled", "mobile data", "бэкап"), in the app language; a tap opens its section, scrolls to the setting and highlights it.
- ✅ **Sign-ins in their own store.** Tracker tokens and the automatic backup folder moved out of the settings file into `lume_private`, once, on the first start. Android's cloud backup keeps the settings again; sign-ins still stay out.
- ✅ **Split `SettingsScreen.kt`** (about 770 lines) into `ui/screens/settings`: one file per larger section, the short ones together, plus the catalogue the search reads.

### v0.6.3: reading lists ✅ released 2026-10-20

Release notes: [docs/releases/v0.6.3.md](releases/v0.6.3.md).

- ✅ **Share a reading list.** The share button in the Library (or in the selection bar) writes the titles on screen, the open category or the selected titles to a small JSON file: names, sources, covers and web links, no progress. Share it through any app or save it as a file.
- ✅ **Open a reading list.** A list shared to Lume, opened from a file manager or picked under **Settings → Backup and restore → Open a reading list** shows its titles: the ones already in the library are marked, titles from sources Lume doesn't have are found through their web link where another source has it, and the rest can be added, to a category if you want.
- ✅ **Fix:** a link or file shared to Lume while Lume was already open sometimes landed in the window in the background; now the window on screen opens it.
- ✅ **Split `LibraryScreen.kt`** (about 850 lines) into the screen, the grid and list items, and the options sheet with its dialogs.

### v0.6.4: similar titles ✅ released 2026-10-20

Release notes: [docs/releases/v0.6.4.md](releases/v0.6.4.md).

- ✅ **Similar titles.** The title screen shows a row of covers that AniList users recommend for the title (no account needed); when AniList has none and you are signed in to MyAnimeList, MyAnimeList's. A tap opens global search with that name. Titles linked to a tracker use the link; others are matched by name and only shown when the match is clear. Kept in memory per title; adult recommendations follow the 18+ search setting.
- ✅ **A setting to turn it off** (Settings → Sources and search → Similar titles), found by settings search; [PRIVACY.md](../PRIVACY.md) says what is sent.
- ⏸️ **Split `Database.kt`.** Moved on: this release doesn't change the database, so it waits for the next release that does.


### v0.6.5: polish from emulator testing ✅ released 2026-10-20

Release notes: [docs/releases/v0.6.5.md](releases/v0.6.5.md).

- ✅ **Settings hints.** Space between the chips and the hint under them in Downloads (pages at once), Network (DNS over HTTPS) and Backup (automatic backups).
- ✅ **Reader title bar.** The chapter line is light on every page, and the dark band behind the bar lasts longer.
- ✅ **Empty pack link.** Tapping Import with an empty field shows "Enter a pack link first".
- ⏸️ **Not bugs:** incognito is off by default in code; the late switch responses seen in testing looked like emulator lag.


### v0.6.6: source filters and browse sections ✅ released 2026-10-20

Release notes: [docs/releases/v0.6.6.md](releases/v0.6.6.md).

- ✅ **The site's own filters.** The filter panel loads genres, tags, types, statuses and formats from the site (MangaDex, MangaLib/YaoiLib/HentaiLib, ReManga, Madara packs, Asura, HiveToons/Vortex, WeebCentral). Genres and tags can be included or excluded where the site allows it, with "has all / has any". Long lists fold, show what is chosen and have a find field. The panel opens at once and adds the lists when they arrive.
- ✅ **Filters with search text** on MangaDex, the Lib sites, Madara, HiveToons and WeebCentral. ReManga and Asura filter only their catalogue; the panel says so when there is text.
- ✅ **Browse sections** in the catalogue: chips above the popular list ("New titles", "Top rated", "Completed", "Most viewed", "Manhwa"…), per source.
- ✅ **More title details:** author and artist, year and the site's score on the title screen; MangaDex type from the original language; more alternative names from the Lib sites, ReManga, Madara and Asura, so "Found for you" matches more titles.
- ✅ The weekly live check also loads every source's filters, searches with one genre and opens every browse section.
- ⏸️ **MangaBuff filters:** the site is geo-blocked from where this was built, so its parameters couldn't be checked. **MangaKatana:** genre pages not used yet.
- **Lib site descriptions** are empty: the API no longer sends them to visitors who aren't signed in (already so in v0.6.5). Separate fix.

### v0.7.0: sync and reader controls ✅ released 2026-10-21

Release notes: [docs/releases/v0.7.0.md](releases/v0.7.0.md).

- ✅ **Sync between devices** through a folder (Syncthing, a cloud drive app) or a WebDAV server: library, categories, chapter spots, read marks, page bookmarks, linked sources, tracker links, per-title reader settings and packs. One file per device; newest change wins, furthest chapter spot wins, "Continue" by reading time; removals spread, a wiped install never removes anything elsewhere.
- ✅ **Passphrase** encryption of the sync files; sync now, in the background and at start; status line.
- ✅ **Reader:** page slider, orientation lock, webtoon auto-scroll and tap zones, sepia / grey / warm filters, e-ink mode, own brightness, share or save a page.
- ⏸️ Google Drive; ViewModel tests (next v0.7.x).

### v0.7.1: subscribed downloads ✅ released 2026-10-26

Release notes: [docs/releases/v0.7.1.md](releases/v0.7.1.md).

- ✅ **Subscriptions:** a bell on a library title's page; "Download new chapters" is Off / All titles / Subscribed, and the old switch carries over. Subscriptions go into backups and sync and move with "Make main" (database 9 → 10).
- ✅ **Build:** AGP 9.2.1 (pinned for Android Studio), Gradle 9.8.2, OkHttp 5.5.1, kotlinx-serialization 1.12.0, DataStore 1.3.0.
- ⏸️ Bulk subscribe from the library selection; ViewModel tests.

### v0.7.2: MangaLib bookmark import ✅ released 2026-10-31

Release notes: [docs/releases/v0.7.2.md](releases/v0.7.2.md).

- ✅ **Bookmark import** for signed-in MangaLib-family sources: every folder, paged, titles of other Lib sites left out; folders become categories (merged by name), titles already in the library keep theirs and gain the folder's.
- ⏸️ Read progress from the site; ViewModel tests.

### v0.7.3: bookmark import fix ✅ released 2026-10-31

Release notes: [docs/releases/v0.7.3.md](releases/v0.7.3.md).

- ✅ **Fix:** `LibBookmarkImport` reaches the `LibSocialEngine` through `SourceRegistry.engineById` instead of casting the wrapped source.
- ✅ **All statuses:** `/bookmarks?status=0` in one paged pass (per-folder fallback); built-in statuses 1–5 named locally when `/bookmarks/folder` fails.
- ⏸️ Read progress from the site; ViewModel tests.

### v0.7.4: read progress with bookmark import ✅ released 2026-10-31

Release notes: [docs/releases/v0.7.4.md](releases/v0.7.4.md).

- ✅ **Last chapter read:** each bookmark's `item` (volume, number, branch) is parsed; the import loads those titles' chapter lists (3 at a time, one retry), matches by volume and number and marks that chapter and every earlier one read.
- ✅ Adds only: existing read marks and continue points stay. The new continue point and read marks are dated by the bookmark's `updated_at`.
- ✅ The chapter list is stored in `library_chapters` on the way, so unread counts are right before the first update check.
- ⏸️ The `progress` field; ViewModel tests.

### v0.7.5: import progress and app updates ✅ released 2026-11-01

Release notes: [docs/releases/v0.7.5.md](releases/v0.7.5.md).

- ✅ **Import progress:** `LibBookmarkImport` runs in the app scope and reports `Fetching → Adding → Progress(done, total) → Finished`; Sources shows a card with the step and a bar, and the summary stays until closed.
- ✅ **Updates:** `AppUpdater` checks `releases/latest` on start (every 6 h at most, can be turned off in About), downloads the APK with progress and opens the system installer. Skip hides a version from the automatic check.
- ⏸️ Import as a WorkManager job that survives process death; release notes rendered as Markdown.

### v0.7.6: request limit, bell pool and library marks ✅ released 2026-11-01

Release notes: [docs/releases/v0.7.6.md](releases/v0.7.6.md).

- ✅ **Request limit:** every `libsocial` source gets 2 req/s on its API host (`LIB_API_RATE_LIMIT`), shared by the three Lib sites; the import's retry waits 5 s.
- ✅ **Import scope:** only statuses 1–4 come over; status 4 ("Прочитано") marks every chapter read.
- ✅ **Bell pool:** `title_prefs.autoDownload` is the bell; `LibraryUpdateWorker` checks the watched titles (at most 20) or the 20 most recently read.
- ✅ **Library marks:** read/total badge; status colour bar in search from category names (`libraryStatusOf`).
- ⏸️ Cross-source matching for search marks; a per-user setting for the pool size.

### v0.7.11: reader and navigation ✅ released 2026-11-01

Release notes: [docs/releases/v0.7.11.md](releases/v0.7.11.md). From the full audit of v0.7.9 (static findings 9, 12, 14 and 17); device check in [v0.7.11-check.md](releases/v0.7.11-check.md).

- ✅ **Tall pages:** in webtoon mode, a page still taller than 4096 px after Coil's capped decode is redrawn with `BitmapRegionDecoder` in slices of at most 2048 decoded px (`ReaderTallPage`, `TallPageTiles`). Old Asura chapters draw on the emulator; a page over 4096 px wasn't confirmed to take the slice path on the device (unit tests cover the slice math).
- ✅ **Navigation:** `openLink` pushes non-tab routes with `launchSingleTop`, replaces an open reader, and keeps tab routes on `navigateToTab`; menu entries use `launchSingleTop`.
- ✅ **Reader:** `ReaderBackBar` over loading and error states; `chapterNeighbours` in reading order; disabled buttons and an edge notice at the ends (the "first chapter" notice was seen on the device, the "last chapter" one wasn't reached).
- ✅ **Pages:** per-page spinner, 70%-of-window placeholder, shared `zoomLayer` / `detectPinchAndPan`.
- ✅ **Accessibility:** shared `BackButton` with `cd_back`.
- ⏸️ Deep links while locked: see the summary above.
- Next: v0.7.12 polish (download queue race, library check result, rotation state, sync, CBZ order, adaptive library grid, menu redesign, doc fixes).

### v0.7.10: safety ✅ released 2026-11-01

Release notes: [docs/releases/v0.7.10.md](releases/v0.7.10.md). From the full audit of v0.7.9 (static findings 1–8, 10 and 11).

- ✅ **Downloads:** temporary `.lume-part` folder, cleaned on failure and on start; safe folder names; queue progress writes only to running rows.
- ✅ **Backup and sync:** restore in one transaction; 64 MB read limit (also unpacked); 48 MB per page.
- ✅ **Source packs:** bundled ids and unsafe ids refused on import, load, restore and sync.
- ✅ **Updates:** host, size and SHA-256 checks; pre-release ordering.
- ✅ **Trackers:** serialized refresh for MyAnimeList, Kitsu and Shikimori; sign-out only on a refused refresh token.
- ✅ **App lock:** content hidden from accessibility services while locked. Turning the lock on without a screen lock was already refused with a message.
- ⏸️ Destructive downgrade and OAuth `state`: see the summary above.
- Next: v0.7.11 reader and navigation (deep links, chapter ends, back-arrow labels, tall strips), v0.7.12 polish.

### v0.7.9: no back animation anywhere ✅ released 2026-11-01

Release notes: [docs/releases/v0.7.9.md](releases/v0.7.9.md).

- ✅ **Back:** `enableOnBackInvokedCallback="false"` in the manifest; no system or in-app predictive-back animation is left. The opt-out is honoured on API 37 / targetSdk 37.

### v0.7.8: login, back and a fast library ✅ released 2026-11-01

Release notes: [docs/releases/v0.7.8.md](releases/v0.7.8.md).

- ✅ **Login:** with no Lume token, a `localStorage.auth` found before the user touches the page is treated as stale: `LibWebSession.wipeAll` deletes all WebView storage and cookies (re-setting the sources' clearance cookies) and the page reloads; stale tokens are never saved. Logout expiry also sends `Secure; SameSite=None` variants.
- ✅ **Back:** pop transitions are `None`; `BackGestureCard`, `BackGesture.kt` and the direct navigationevent dependency are removed.
- ✅ **Library:** `observeLibraryStats` is one grouped pass instead of correlated `NOT IN` subqueries (synthetic 600 titles: 126 s → 0.4 s, same results); the library waits for the first answer instead of flashing empty.

### v0.7.7: Lib sign-out and back animation ✅ released 2026-11-01

Release notes: [docs/releases/v0.7.7.md](releases/v0.7.7.md).

- ✅ **Sign-out:** `LibWebSession` clears the site's and `lib.social`'s WebView storage and cookies, keeping anti-bot clearance; the login WebView opts into autofill.
- ✅ **Back animation:** `BackGestureCard` rounds and shadows the leaving screen while a gesture is held (`NavigationEventDispatcher.transitionState`) or the entry pops; pop exit scales to 0.9.
- ⏸️ Google sign-in inside the web view.
