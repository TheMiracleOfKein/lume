# Catalogue engines (Lume)

Lume does **not** load Mihon/Tachiyomi extension APKs or arbitrary DEX at runtime.
The unit of value is a **first-party platform engine** (Kotlin). JSON files are only
**packs** that clone a supported family without new code.

## Model

| Layer | What it is | How it arrives |
| --- | --- | --- |
| **Platform engine** | Kotlin adapter (`MadaraEngine`, `MangaDexEngine`, …) | App update |
| **Family pack** | JSON with `engine` = `madara` / `libsocial` / `hivetoons` | Bundled assets or URL import |
| **Site engine** | One-site Kotlin adapter (`weebcentral`, `demonic`) | App update only (not importable) |
| **Local** | Folders / CBZ | Always built-in |

```
JSON pack ──► SourceRegistry ──► known engine id ──► Kotlin class
                    │
                    └── unknown / non-family import ──► rejected
```

## Core platform engines

| Engine id | Role | Packs? |
| --- | --- | --- |
| `mangadex` | MangaDex API | no (identity manifest only) |
| `libsocial` | cdnlibs (MangaLib family), image pages | yes (`siteId`, `apiBase`) |
| `ranobelib` | cdnlibs (RanobeLib), chapter text; novel module only | no |
| `madara` | WordPress Madara theme | yes (`baseUrl`, paths, ajax) |
| `hivetoons` | Iken API (catalogue, details, chapters) + reader HTML | yes (`baseUrl`, `apiBase`) |
| `mangakatana` | MangaKatana HTML | no |
| `asura` | Asura API (`api.asurascans.com`, front `asurascans.com`) | no |
| `remanga` | ReManga API | no |
| `mangabuff` | MangaBuff HTML | no |
| `inkstory` | InkStory pages (data script) | no |
| `local` | Offline folders/CBZ | no |
| `royalroad` | Royal Road HTML, chapter text; novel module only | no |
| `scribblehub` | Scribble Hub HTML + `admin-ajax.php` chapter list, chapter text; novel module only | no |
| `novelfull` | NovelFull-theme HTML (NovelFull, NOVGO) + `/ajax-chapter-option` chapter list, chapter text; novel module only | no |

## Bundled sources (assets)

| ID | Engine | Notes |
| --- | --- | --- |
| `mangadex` | `mangadex` | Core |
| `mangakatana` | `mangakatana` | Core HTML |
| `asura` | `asura` | Core API |
| `mangalib` / `slashlib` / `yaoilib` / `hentailib` | `libsocial` | Family packs (`siteId`) |
| `ranobelib` | `ranobelib` | Novels, `siteId` 3; in `feature/novel` assets, so the full and ranobe builds only |
| `royalroad` | `royalroad` | English novels; `feature/novel` assets |
| `scribblehub` | `scribblehub` | English novels; `feature/novel` assets |
| `novelfull` / `novgo` | `novelfull` | Translated novels in English; `feature/novel` assets |
| `mangaread` | `madara` | Family pack example |
| `hivetoons` / `vortexscans` | `hivetoons` | Family packs |
| `remanga` | `remanga` | Russian API |
| `mangabuff` | `mangabuff` | Russian HTML |
| `inkstory` | `inkstory` | Russian, reads the site's pages |
| `local` | `local` | Injected in code |

Optional Madara mirrors (Cloudflare-prone) live under [`packs/madara`](packs/madara/likemanga.json) — import via Sources UI if needed.

LibSocial sources and RanobeLib support **in-app login** (WebView → `localStorage.auth`) and bookmark import from the Sources screen.

### RanobeLib

RanobeLib uses the same API as MangaLib (`LibSocialBase` in `core/data`: catalogue, filters, details, chapters with teams, paid chapters left out, bookmarks), with `Site-Id: 3` and title pages under `/ru/book/`. The chapter answer (`/manga/<slug>/chapter?number&volume&branch_id`) has `content` and `attachments` instead of `pages`. `content` comes in two shapes, the same for ranobe, light novels and web novels:

- a rich-text document (`{"type":"doc","content":[…]}`): paragraphs of text nodes with bold / italic marks and `textAlign`; `LibChapterText` turns it into HTML (headings, lists, breaks, rules and images by attachment name too; unknown nodes keep their text);
- an HTML string (`<p data-paragraph-index>…</p>` and `<img src="https://ranobelib.me/uploads/…">`, sometimes bare between paragraphs).

Pictures need the `ranobelib.me` Referer (403 without). In HTML chapters the `<img>` links are kept: `attachments` URLs sometimes differ (a trailing `-` in the slug) and answer 500. A rich-text chapter with an image node hasn't been seen yet; it maps through `attachments`. `api.cdnrulib.link` serves the same answers as `api.cdnlibs.org`.

### Royal Road and Scribble Hub

Both are plain HTML sites behind Cloudflare; the app's challenge WebView solves the check, then requests carry its cookies and User-Agent. Title ids are paths (`/fiction/<id>/<slug>`, `/series/<id>/<slug>/`), chapter ids are chapter paths. `NovelHtml` in `feature/novel` cleans the chapter element (scripts, ads, forms and attributes dropped, `data-src` images made absolute, links reduced to their text).

- **Royal Road:** lists are `.fiction-list-item` (`/fictions/weekly-popular`, `/fictions/latest-updates`, `/fictions/search?title=`, `?page=N`). The title page has every chapter in `window.chapters = [...]`, with `order` (the chapter number is `order + 1`) and `isUnlocked`; locked (paid) chapters are left out. The text is `.chapter-inner.chapter-content`. Each chapter hides an anti-copy line through a class that the page's `<style>` sets to `display: none`; `NovelHtml.hiddenClasses` reads those rules and the elements are removed. Author's notes are left out.
- **Scribble Hub:** lists are `.search_main_box` (`/series-ranking/?sort=1&order=1`, `/latest-series/`, `/series-finder/?sf=1&sh=<query>&order=desc&sort=ratings`, `pg=N`); the page count is only in the pagination script (`items: N`). The title page shows the newest 15 chapters; the whole list is a POST to `/wp-admin/admin-ajax.php` (`action=wi_getreleases_pagination`, `pagenum=-1`, `mypostid=<id>`) answering the same `li.toc_w` rows, whose `order` attribute is the chapter number. The text is `#chp_raw` without `.wi_authornotes`.

### NovelFull and NOVGO

One engine, `novelfull`, for two sites on the same theme: NovelFull (`novelfull.com`) and NOVGO (`novgo.net`, where `allnovelfull.net` now redirects). Both carry translated Chinese, Korean and Japanese novels, light novels among them (e.g. Overlord, Classroom of the Elite). Plain HTML; no challenge was seen when they were added. The URLs are shared, the list and title page markup is not, so each selector names both layouts.

- **Lists:** `/most-popular`, `/latest-release-novel`, `/hot-novel`, `/completed-novel`, `/genre/<Name>` (spaces as `+`) and `/search?keyword=`, all paged with `?page=N`. Rows are `.list-truyen .row` with `h3.truyen-title` (NovelFull) or `.li-row` with `h3.tit` (NOVGO); a next page is `li.next a` or `a[rel=next]`. The site searches by text only and a genre page has no other filter, so the filter panel has a list choice and one genre; text wins over the genre, the genre over the list.
- **Title:** ids are paths (`/overlord-ln.html`). Details come from `.info` / `.m-imgtxt`; the score is `#rateVal` (out of 10) or `#novel-score[data-avg]` (out of 5, doubled).
- **Chapters:** the title page lists 50 per page; the whole list, in reading order, is the `<select>` of `/ajax-chapter-option?novelId=<id>`, where the id is the title page's first `data-novel-id`. The chapter number is the place in that list.
- **Text:** `#chapter-content`, without ad frames and NovelFull's closing "If you find any errors … report chapter" line.

One-site engines (`weebcentral`, `demonic`) remain in the binary for compatibility but are **not** in default assets and **cannot** be imported as JSON. Sample manifests: [`packs/site`](packs/site/weebcentral.json).

**Not bundled:** Com-X.life, because its custom browser-fingerprint gate is not detected by the app's challenge flow.

### InkStory

[InkStory](https://inkstory.net) has no public API, so the engine reads the data script
the site's own pages carry (it can break when the site is redeployed).

- Popular, latest, search with genre and tag filters, details, chapters and pages work without an account.
- Titles the site shows only to signed-in readers, library members or subscribers are left
  out of the lists. If one is opened from a link, the chapter list says so instead of staying empty.
- Paid chapters are skipped. Adult-flagged titles are not listed (the site serves them to signed-in readers only).
- A title with several translation teams lists every team's chapters; pick the team in the chapter list.
- Chapter pages are served masked. The engine tags each page URL with the key the site publishes,
  and the app's HTTP client unmasks the image (reader, downloads and cache alike).
- Requests are limited to one per second on the site (`rateLimit`).

## Import rules

`SourceRegistry.importJson` / `importUrl` accept only:

- `engine`: `madara` | `libsocial` | `hivetoons`

Anything else fails with a clear message: new engines ship with the app.

## ExtensionLoader

**Out of scope.** Reasons: security / Play policy, API drift, licensing, native stack.
See historical notes in [MIHON_EXTENSIONS.md](MIHON_EXTENSIONS.md).

## `engineConfig` keys

| Key | Engines | Meaning |
| --- | --- | --- |
| `apiBase` | most API engines | API root (e.g. `https://api.cdnlibs.org/api`) |
| `siteId` | `libsocial`, `ranobelib` | cdnlibs site id (`1` MangaLib, `2` SlashLib (formerly YaoiLib), `3` RanobeLib, `4` HentaiLib) |
| `webPath` | `libsocial`, `ranobelib` | title page path on the site: `manga` (default) or `book` (RanobeLib) |
| `mangaPath`, `useAjaxLoadMore`, `chapterUrlSuffix` | `madara` | catalogue path, `admin-ajax` list loading, reader query |
| `languages` | `mangadex` | comma-separated chapter languages |
| `dataSaver` | `mangadex` | `true` serves compressed pages |
| `rateLimit` | any | max requests/second to the API host (or site host without `apiBase`) |
| `imageHosts` | any | extra image CDN domains that need this source's `Referer` |
| `pagesNeedLogin` | `libsocial` | `true`: the site shows chapter pages only to signed-in users, so a 404 asks the user to sign in |

Image requests get a `Referer` automatically when the image host shares a domain with the
source's `baseUrl` / `apiBase`, is listed in `imageHosts`, or was seen in that source's results.

## Networking notes

- One User-Agent is used for catalogue calls, image loading and the challenge WebView:
  Cloudflare binds `cf_clearance` to it.
- Cookies are stored in the WebView `CookieManager`, so a solved challenge or a site login
  applies to every request and survives restarts.
- Only real interactive challenges (Cloudflare / DDoS-Guard) open the WebView. Region blocks
  and auth failures surface as HTTP errors with a readable message.
- GET requests are retried on 429 / 502 / 504 / non-challenge 503 and dropped connections.

## Live source checks

JVM tests drive every bundled source against the real site (popular → page 2 → latest →
search → details → chapters → pages → first image):

```bash
./gradlew :feature:comics:testDebugUnitTest -Plume.live
./gradlew :feature:comics:testDebugUnitTest -Plume.live -Plume.live.sources=asura,mangadex
```

Sources unreachable from the test machine (geo-blocks, challenges needing a WebView) are
reported as `SKIP`, not failures. Plain `testDebugUnitTest` runs only offline unit tests.

RanobeLib has its own check in the novel module (popular → search → details → chapters →
chapter text): `./gradlew :feature:novel:testDebugUnitTest -Plume.live`. The same command runs
`LiveEnglishNovelsTest` for Royal Road, Scribble Hub, NovelFull and NOVGO; a Cloudflare check is reported as
skipped. The weekly workflow runs all of them.
Unit tests run on OkHttp's JVM build (the Android one reads its public-suffix list from
Android assets, which JVM tests don't have).

## Self-hosted servers (OPDS, Komga, Kavita)

These engines are not bundled and cannot be imported as JSON packs. You add one in
Sources → "Add server" (type, address, sign-in). It becomes a source with the id
`server-<uuid>` and is stored like a pack; its sign-in is not part of the manifest.

- **Sign-in.** A user name and password, or an API key, kept in `ServerCredentialStore`:
  AES-GCM with a key in the Android Keystore, written to its own DataStore file that is left
  out of Android backups. Nothing is logged. `ServerAuthInterceptor` adds the header (Basic,
  `X-API-Key`, or Kavita's Bearer token) to requests for the registered origin only
  (scheme, host and port). It is a network interceptor, so a redirect to another host does
  not carry it.
- **Plain HTTP.** The platform allows cleartext, and `CleartextGuardInterceptor` refuses
  `http://` for every host that is not a server you added. The add dialog warns for `http://`.
- **OPDS** (`opds`): the address is the catalogue root (OPDS 1.2 Atom or 2.0 JSON, detected
  from the body). A feed entry that opens a feed is a title, a book is a chapter. Pages come
  from an OPDS-PSE stream link when the book has one; otherwise the CBZ, CBR, CB7 or PDF
  acquisition is downloaded to the cache and read like a local file. Search uses the
  OpenSearch template. The catalogue sections of the root show from the second time you open
  the source.
- **Komga** (`komga`): `/api/v1` series, books and pages; sign-in Basic or `X-API-Key`.
  Reading progress goes back with `PATCH /books/{id}/read-progress`.
- **Kavita** (`kavita`): signs in with `Plugin/authenticate` (API key) or `Account/login`, keeps
  the JWT and signs in again on a 401. Page and cover URLs carry `apiKey=-`, which the HTTP
  client replaces, so the key is never stored in history or the image cache. Progress goes
  back with `Reader/progress` and `Reader/mark-chapter-read`.
- Both can be limited to one library in the source's settings.

Tests: `OpdsParsingTest`, `KomgaParsingTest`, `KavitaParsingTest` (hand-written fixtures in
`fixtures/opds`, `fixtures/komga`, `fixtures/kavita`), `ServerAccessTest`,
`ServerCredentialStoreTest`.

## Saved pages (offline parser tests)

Every engine has a `<Engine>ParsingTest` that parses saved site responses, so a parser change
is checked without the live sites and without a network. Each engine splits fetching from
parsing: a fetch method calls an `internal fun parseXxx(body, …)` (`parseTitleList`,
`parseDetails`, `parseChapters`, `parsePages`). When one result needs several requests
(chapter pages, a title page plus an AJAX list), each body is passed separately.

Fixtures live in `feature/comics/src/test/resources/fixtures/<source id>/<case>.{json,html}` and are read
with `Fixtures.text("<source id>/<case>.json")`. The cases are `popular`, `search`, `details`,
`chapters` and `pages`; a second response of the same case is `<case>-2` (for example
`details-2.json`, the MangaDex statistics). Large lists are cut to a few dozen entries, and
scripts and styles are dropped from big HTML pages that the parser does not read from.

Record or refresh fixtures from the live sites:

```bash
./gradlew :feature:comics:testDebugUnitTest --tests '*LiveSourcesTest*' -Plume.live -Plume.live.record -Plume.live.sources=asura,mangalib
```

`-Plume.live.record` saves the response bodies of the first title the live check walks
through. Naming `weebcentral` or `demonicscans` in `-Plume.live.sources` also runs the one-site
samples from `docs/packs/site`. Sources that cannot be reached (geo-block, challenge) write
nothing; their fixtures are written by hand from the shape the parser reads, and the test's
comment says so (MangaDex and MangaBuff at the time of writing). After recording, shrink big
files and run the offline tests: `./gradlew :feature:comics:testDebugUnitTest`.

The `<Engine>ParsingTest` classes (and the novel ones in `feature/novel`: Royal Road, Scribble Hub,
RanobeLib) live in `src/sharedTest`, so the same assertions also run on a device or emulator with
Android's regex engine (ICU), which accepts less than the JVM's: a pattern that passes the JVM
tests can still fail on a phone. The fixtures and the bundled source manifests are test resources
for both runs. `ComicsEnginesAndroidTest` and `NovelEnginesAndroidTest` also build every engine on
the device. Run them with an emulator or phone attached (the `Emulator tests` workflow does this on
every PR):

```bash
./gradlew :feature:novel:connectedDebugAndroidTest :feature:comics:connectedDebugAndroidTest
```

### Coverage gaps

Not every engine has all five cases saved as their own file. Where a case is missing, the test
reads that engine's chapter list from its title page, or the case is simply not tested yet.

| Source | Saved as own file | Gap |
| --- | --- | --- |
| `asura` | popular, search, details, chapters, pages | none |
| `demonicscans` | popular, search, details, pages | chapters are read from `details.html`; no `chapters` file |
| `hivetoons` | popular, search, details, chapters, pages | none (pages are HTML, the rest JSON) |
| `inkstory` | popular, search, details, chapters, pages, latest | none (includes a restricted-chapters case) |
| `likemanga` | popular, search, details, pages | chapters are read from `details.html`; no `chapters` file |
| `mangabuff` | popular, search, details, pages, `chapters-2` | no base `chapters` file; `chapters-2` is a second response |
| `mangadex` | popular, search, details, chapters, pages | none; `details-2` is the statistics response (fixtures hand-written, see above) |
| `royalroad` (novel) | popular, popular-page2, latest, search, details, chapter | chapters are read from `details.html` (the page script); saved in a browser, chapter text cut to a few paragraphs |
| `scribblehub` (novel) | popular, latest, search, details, chapter | chapters are read from the 15 rows of `details.html` (the `admin-ajax.php` answer has the same markup but wasn't saved); `search` is the finder without a query; the chapter's text was replaced by neutral sample paragraphs |
| `novelfull`, `novgo` (novel) | popular, latest, search, details, chapters, chapter | none; each chapter's text was replaced by neutral sample paragraphs (NovelFull's ad frames and "report chapter" line kept) |
| `mangakatana` | popular, search, details, pages | chapters are read from `details.html`; no `chapters` file |
| `mangalib` | popular, search, details, chapters, pages | none |
| `remanga` | popular, search, details, pages, `chapters-page1`, `chapters-last` | chapters split into pages; no single `chapters` file |
| `vortexscans` | popular, search, details, chapters, pages | none (pages are HTML, the rest JSON) |
| `weebcentral` | popular, search, details, chapters, pages | none |
| `komga`, `kavita`, `opds` | server responses, not per-case | only `pages` for Komga; server feeds are tested separately |

Known gaps to fill when a source can be recorded again: a `chapters` file for DemonicScans,
LikeManga, MangaBuff and MangaKatana, and a `latest` case for any engine that has a latest list.
