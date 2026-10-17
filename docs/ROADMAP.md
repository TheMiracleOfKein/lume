# Lume roadmap

This roadmap lists what Lume has shipped, what comes next, and the bigger features that set it apart.

Effort is rough: **S** = under a day, **M** = a few days, **L** = a week or more.

Status:
- ✅ done
- 🟡 partly done (the note says what is left)
- ⏸️ on hold (the note says why)
- No mark: planned

## v0.3: exact resume and smooth motion ✅ released 2026-10-17

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
- ⏸️ **Rounded screen corners during the back gesture.** Removed in v0.3. Showing them only while a back gesture is in progress needs a Compose API (`Transition.isSeeking`) that Compose 1.12 keeps internal. Bring them back when that API is public.
- ✅ **Measured baseline profile.** Done in v0.4: the `:baselineprofile` module records it on an emulator.
- 🟡 **Device testing of v0.3.** v0.3 was released after builds and unit tests only. The device checklist (resume, motion, notifications, R8 build) is still to be worked through on real phones.

## v0.4: foundation, library and chapters ✅ released 2026-10-17

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
- 🟡 **Device testing of v0.4.** Checked on an emulator, including the signed R8 release APK: the app starts, the migration runs, the library grid and its counts, chapter selection, the reader, and "Update now". The rest still needs real phones, together with the v0.3 checklist.
- **Startup numbers.** `StartupBenchmark` exists but has not been run yet; run it on a real phone to measure the profile's gain (S).

## v0.5: sources, downloads, backups, trackers

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
- 🟡 MyAnimeList, Kitsu, MangaUpdates. All three are built. MyAnimeList needs Lume's client ID (`LUME_MAL_CLIENT_ID` in `local.properties`) before its sign-in works in a release; none of the three has been tried with a real account yet.

**App**
- ✅ Follow the system theme, Material You colors, pure-black AMOLED theme.
- ✅ Navigation rail on tablets (screens 600 dp and wider).
- ✅ About and licenses page; clear cache and cookies; DNS over HTTPS.
- ✅ Incognito mode, app lock, hide content in the recent-apps screen.
- ✅ Translate chapter labels that sources send only in Russian.

**Left over from v0.5**
- 🟡 **Device testing of v0.5.** Checked on an emulator: search with filters only and paging, a shared MangaDex link, the Sources, Settings and Downloads screens, with no crashes. Tracker sign-ins, Mihon import with a real backup, the download notification, CBZ, local import, app lock and the tablet rail still need real devices and accounts.

## Reader backlog

These land across releases.

- Page slider (S)
- Load the previous chapter above the current one in seamless mode (M)
- Crop white borders; split wide pages; two-page spreads (M)
- Webtoon auto-scroll; tap zones in webtoon mode (S)
- Color filters, e-ink mode, custom brightness (S)
- Save or share a page with a link to its source (S)
- Choose how many pages to load ahead; split very tall images so they load faster and use less memory (M)
- Sharp zoom (the image is decoded again at the zoomed size) (M)
- Orientation lock (S)

## Signature features

### 1. One title, many sources (v0.6, L)

A library entry can be backed by several sources at once.

- **Merged chapter list.** Chapters from all linked sources are combined by volume and number. Each chapter comes from the best available source and shows a small source badge.
- **Found for you.** When you add a title, Lume quietly searches your other sources and suggests "Also on X, Y". It matches titles, alternative titles and authors.
- **Never stuck.** If a chapter fails on one source (Cloudflare, region block, removed), Lume loads it from the next one. Your position carries over, down to the spot inside the page; the per-chapter positions from v0.3 make this possible.
- **First to publish.** Update notifications tell you which source got the new chapter first.
- **One-tap migration.** Move progress, page bookmarks, categories and tracker links to another source.

### 2. On-device AI reading (v0.7+, L)

Everything runs on the phone, with no account. It ships in a separate build flavor, so a fully open-source build stays possible.

- **Guided panel view.** Lume finds the panels on each page and steps through them one tap at a time, zooming smoothly, in the page's reading direction. The same detection powers border cropping and spread splitting.
- **Translate raws.** Text recognition (Japanese, Korean, Chinese, Latin scripts) plus on-device translation. Lume finds each speech bubble, clears it, and draws the translated text inside.
  - Results are saved per page.
  - It can be turned on per title.
  - It works offline once the language is downloaded.
- **Sharper old scans.** Optional AI upscaling for low-resolution pages. It runs piece by piece on the GPU, and the result is cached.

Panel detection starts with classic image processing, which needs no model. Learned models stay optional because of their size, their speed on older phones and the licenses of their training data.

### 3. Sync and continuity (v0.5–0.6, M–L)

This builds directly on the exact positions and page bookmarks from v0.3.

- **See exactly where you stopped.** "Continue" cards show a small picture of the panel you were reading when you left. Page bookmarks use the same picture. Today, page bookmarks show a crop of the page image.
- **Home-screen widget.** One to three "Continue" cards; a tap opens the exact spot. App shortcuts for "Continue <title>".
- **Sync between devices.** Library, progress, bookmarks and categories sync through WebDAV (for example Nextcloud), a shared folder (works with Syncthing) or, optionally, Google Drive.
  - The newest change wins.
  - For reading progress, the furthest position wins unless you reset it.
