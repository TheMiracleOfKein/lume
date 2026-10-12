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
| `local` | `local` | Injected in code |

Optional Madara mirrors (Cloudflare-prone) live under [`packs/madara`](packs/madara/likemanga.json) — import via Sources UI if needed.

LibSocial sources support **in-app login** (WebView → `localStorage.auth`) from the Sources screen.

One-site engines (`weebcentral`, `demonic`) remain in the binary for compatibility but are **not** in default assets and **cannot** be imported as JSON. Sample manifests: [`packs/site`](packs/site/weebcentral.json).

**Not bundled yet:** InkStory (opaque SPA API), Com-X.life (antibot gate) — need more reverse-engineering.

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
