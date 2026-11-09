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
| `libsocial` | cdnlibs (MangaLib family) | yes (`siteId`, `apiBase`) |
| `madara` | WordPress Madara theme | yes (`baseUrl`, paths, ajax) |
| `hivetoons` | Iken API (catalogue, details, chapters) + reader HTML | yes (`baseUrl`, `apiBase`) |
| `mangakatana` | MangaKatana HTML | no |
| `asura` | Asura API (`api.asurascans.com`, front `asurascans.com`) | no |
| `remanga` | ReManga API | no |
| `mangabuff` | MangaBuff HTML | no |
| `inkstory` | InkStory pages (data script) | no |
| `local` | Offline folders/CBZ | no |

## Bundled sources (assets)

| ID | Engine | Notes |
| --- | --- | --- |
| `mangadex` | `mangadex` | Core |
| `mangakatana` | `mangakatana` | Core HTML |
| `asura` | `asura` | Core API |
| `mangalib` / `yaoilib` / `hentailib` | `libsocial` | Family packs (`siteId`) |
| `mangaread` | `madara` | Family pack example |
| `hivetoons` / `vortexscans` | `hivetoons` | Family packs |
| `remanga` | `remanga` | Russian API |
| `mangabuff` | `mangabuff` | Russian HTML |
| `inkstory` | `inkstory` | Russian, reads the site's pages |
| `local` | `local` | Injected in code |

Optional Madara mirrors (Cloudflare-prone) live under [`packs/madara`](packs/madara/likemanga.json) — import via Sources UI if needed.

LibSocial sources support **in-app login** (WebView → `localStorage.auth`) from the Sources screen.

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
| `siteId` | `libsocial` | cdnlibs site id (`1` MangaLib, `2` YaoiLib, `4` HentaiLib) |
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
./gradlew :app:testDebugUnitTest -Plume.live
./gradlew :app:testDebugUnitTest -Plume.live -Plume.live.sources=asura,mangadex
```

Sources unreachable from the test machine (geo-blocks, challenges needing a WebView) are
reported as `SKIP`, not failures. Plain `testDebugUnitTest` runs only offline unit tests.

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

Fixtures live in `app/src/test/resources/fixtures/<source id>/<case>.{json,html}` and are read
with `Fixtures.text("<source id>/<case>.json")`. The cases are `popular`, `search`, `details`,
`chapters` and `pages`; a second response of the same case is `<case>-2` (for example
`details-2.json`, the MangaDex statistics). Large lists are cut to a few dozen entries, and
scripts and styles are dropped from big HTML pages that the parser does not read from.

Record or refresh fixtures from the live sites:

```bash
./gradlew :app:testDebugUnitTest --tests '*LiveSourcesTest*' -Plume.live -Plume.live.record -Plume.live.sources=asura,mangalib
```

`-Plume.live.record` saves the response bodies of the first title the live check walks
through. Naming `weebcentral` or `demonicscans` in `-Plume.live.sources` also runs the one-site
samples from `docs/packs/site`. Sources that cannot be reached (geo-block, challenge) write
nothing; their fixtures are written by hand from the shape the parser reads, and the test's
comment says so (MangaDex and MangaBuff at the time of writing). After recording, shrink big
files and run the offline tests: `./gradlew :app:testDebugUnitTest`.
