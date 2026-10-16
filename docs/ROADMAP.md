# Lume roadmap

This roadmap lists what Lume improves next and the bigger features that set it apart. Effort is rough: **S** = under a day, **M** = a few days, **L** = a week or more.

## v0.3 — Exact resume and smooth motion (in progress)

- **Exact resume inside a chapter.** Lume remembers where you are *inside* a page, not just which page, for every chapter separately. Reopening lands on the same spot, even in very tall webtoon slices, after rotation, after Android closes the app, and after seamless reading into the next chapter.
- **Page bookmarks.** Pin any spot with an optional note. Bookmarks appear on the title screen and jump back to the exact spot.
- **A chapter counts as read when you finish it**, not when you open it. Trackers update at the same moment.
- **Smoother app.** No more jumps when the bottom bar hides or shows. The reader does not resize when its controls hide. Screens keep their content when you go back. Settings open without switches animating. Predictive back follows your finger.
- **Fixes.** New chapters always appear on the title screen. Update notifications ask for permission and open the library. Background updates no longer miss sources. Interrupted downloads resume. "Clear" on Home asks first.

## v0.4 — Foundation, library and chapters

**Foundation**
- ViewModels for every screen; a small app container instead of `LumeApp.instance` lookups (M)
- Room migration tests; GitHub Actions with build, unit tests and lint (S)
- Tests for reading order, backup round-trip and tracker matching (S)

**Library**
- Grid, compact and list modes; sorting; filters (unread, downloaded, started, tracking, completed); search in the library (M)
- Unread and downloaded counts on each title (S)
- Select several titles for bulk actions (M)
- Rename, delete and reorder categories; default category (S)

**Updates**
- An Updates screen with new chapters from the whole library (History moves to the menu) (M)
- "Update now" and pull to refresh (S)
- Detect new chapters by id instead of by count (S)

**Chapters**
- Mark read / unread, "mark previous as read", filters (S)
- Show and filter by scanlator / translation team; hide duplicate chapters on MangaLib, YaoiLib and HentaiLib (M)
- "Download next N / all unread", delete downloaded chapters, range select (S)

## v0.5 — Sources, downloads, backups, trackers

**Sources**
- Filter panel for catalogue search (the filter model already exists in `domain/Filters.kt`) (M)
- More results pages in search; search history (S)
- Per-source settings, for example MangaDex languages and content rating (M)
- Browse any source without changing the active one; enable, disable and pin sources (S)
- Open a site link shared from the browser directly in Lume (M)
- A pack index with update checks (M)

**Downloads**
- Downloads run as a background job with a notification; pause, resume, reorder and retry (M)
- Several downloads at once per source; check that downloaded files are really images (S)
- Save as CBZ; choose the storage folder (S / M)
- Download new chapters automatically; delete chapters after reading; storage usage (S)

**Local files**
- Import a folder from your device; ComicInfo.xml and covers; clean temporary files (M)

**Backups**
- Save and open backups anywhere on the device; automatic backups with rotation; include settings (S)
- **Import Mihon / Tachiyomi backups (`.tachibk`)** so moving to Lume takes one step (M)

**Trackers**
- Shikimori stays signed in (token refresh) (S)
- Edit status, score and dates; link automatically when adding to the library (S)
- MyAnimeList, Kitsu, MangaUpdates (L)

**App**
- Follow the system theme, Material You colors, pure-black AMOLED theme (S)
- Navigation rail on tablets (M)
- About and licenses page; clear cache and cookies; DNS over HTTPS (S)
- Incognito mode, app lock, hide content in the recent-apps screen (S)
- Translate chapter labels that sources send only in Russian (S)

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
- **Found for you.** When you add a title, Lume quietly searches your other sources and suggests "Also on X, Y" (matching titles, alternative titles and authors).
- **Never stuck.** If a chapter fails on one source (Cloudflare, region block, removed), Lume loads it from the next one. Your position carries over, down to the spot inside the page.
- **First to publish.** Update notifications tell you which source got the new chapter first.
- **One-tap migration.** Move progress, page bookmarks, categories and tracker links to another source.

### 2. On-device AI reading (v0.7+, L)

Everything runs on the phone, with no account. It ships in a separate build flavor, so a fully open-source build stays possible.

- **Guided panel view.** Lume finds the panels on each page and steps through them one tap at a time, zooming smoothly, in the page's reading direction. The same detection powers border cropping and spread splitting.
- **Translate raws.** Text recognition (Japanese, Korean, Chinese, Latin scripts) plus on-device translation. Lume finds each speech bubble, clears it, and draws the translated text inside. Results are saved per page. It can be turned on per title and works offline once the language is downloaded.
- **Sharper old scans.** Optional AI upscaling for low-resolution pages. It runs piece by piece on the GPU, and the result is cached.

Panel detection starts with classic image processing, which needs no model. Learned models stay optional because of their size, their speed on older phones and the licenses of their training data.

### 3. Sync and continuity (v0.5–0.6, M–L)

- **See exactly where you stopped.** "Continue" cards show a small picture of the panel you were reading when you left. Page bookmarks use the same picture.
- **Home-screen widget.** One to three "Continue" cards; a tap opens the exact spot. App shortcuts for "Continue <title>".
- **Sync between devices.** Library, progress, bookmarks and categories sync through WebDAV (for example Nextcloud), a shared folder (works with Syncthing) or, optionally, Google Drive. The newest change wins. For reading progress, the furthest position wins unless you reset it.
