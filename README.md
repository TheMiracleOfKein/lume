# Lume

Android manga / comics reader written in **Kotlin** and **Jetpack Compose**.

Lume uses first-party catalogue engines (MangaDex, Madara, LibSocial, and others). JSON packs can clone supported families without shipping new Kotlin code. See [docs/ENGINES.md](docs/ENGINES.md).

![Lume](branding/lume_icon_320.png)

## Features

- Library, home feed, catalogue, search, downloads, offline cache
- Vertical (webtoon) and page reader with seamless chapters, tap zones and volume-key paging
- Fullscreen reading (controls and system bars hide on scroll, tap to show), night dimming, webtoon side margins
- Global search that streams results per source, with per-source timeouts and retry
- Open any title in the browser or share its link
- Source packs + in-app login for supported LibSocial sites
- Optional trackers: Shikimori and AniList
- Backup / restore
- English and Russian UI

## Requirements

- JDK 17+
- Android SDK (API 37 recommended; `minSdk` 26)
- Android Studio or command-line Gradle

## Build

```bash
# Windows
gradlew.bat :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/`.

Tests: `gradlew :app:testDebugUnitTest` (add `-Plume.live` to check bundled sources against the live sites; see [docs/ENGINES.md](docs/ENGINES.md#live-source-checks)).

1. Copy [`local.properties.example`](local.properties.example) to `local.properties`.
2. Set `sdk.dir` (Android Studio usually does this for you).
3. Optionally fill `LUME_*` OAuth keys for tracker login (see below).

## Tracker OAuth (optional)

Shikimori / AniList need **application** client credentials that identify Lume. End users still sign in with their own accounts.

- Keep real keys only in `local.properties` (gitignored).
- Official release builds you produce locally (or later via CI secrets) can bake keys into the APK via `BuildConfig`.
- Keys inside an APK are not strongly secret (they can be extracted). Keeping them out of git still avoids casual leaks and bot scraping of the repository.
- If you build from source without keys, reading works; tracker login will not.

Register your own OAuth apps on the tracker sites if you maintain a fork or personal build.

## Documentation

- [Catalogue engines](docs/ENGINES.md)
- [Localization notes](docs/I18N.md)
- [Sample packs](docs/packs/README.md)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Disclaimer

Lume does **not** host manga or comic content. Catalogues and images come from third-party websites and APIs. Lume is not affiliated with those services. You are responsible for complying with the laws and terms that apply where you use the app.

## License

Licensed under the [Apache License 2.0](LICENSE).

Copyright 2026 TheMiracleOfKein
