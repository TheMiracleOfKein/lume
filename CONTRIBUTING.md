# Contributing to Lume

Thanks for helping improve Lume. Bug reports, source reports, translations and code are all welcome.

## Reporting

- **Bugs:** open a [bug report](https://github.com/TheMiracleOfKein/lume/issues/new?template=bug_report.yml). Include the Lume version, your phone and Android version, and the steps that lead to the problem.
- **A source stopped working:** use the [source report](https://github.com/TheMiracleOfKein/lume/issues/new?template=source_problem.yml). Say which source and what fails (search, title, chapters or pages).
- **Ideas:** open a [feature request](https://github.com/TheMiracleOfKein/lume/issues/new?template=feature_request.yml). Check the [roadmap](docs/ROADMAP.md) first; it may already be planned.
- **Security problems:** don't open an issue; see [SECURITY.md](SECURITY.md).

## Setup

1. Fork and clone the repository.
2. Copy `local.properties.example` to `local.properties` and set `sdk.dir`.
3. Open the project in Android Studio (JDK 17+), or build with Gradle:

   ```bash
   ./gradlew :app:assembleDebug
   ```

Tracker keys (`LUME_*`) are optional; without them only tracker sign-in is missing.

## How the code is organised

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) gives the overview. In short:

- Gradle modules: `:core:model`, `:core:common`, `:core:data`, `:core:ui`, `:core:translate`, `:feature:comics` (manga mode), `:feature:novel` (ranobe mode), `:app` (assembles the `full`, `manga` and `ranobe` flavors) and `:translate-addon` (the Lume Translate app).
- Kotlin, Jetpack Compose, Room, DataStore, WorkManager, Coil 3, Telephoto, Media3, OkHttp and jsoup.
- Shared objects come from `AppContainer`: `LocalAppContainer` in composables, the constructor in ViewModels. Don't add new `LumeApp.instance` lookups.
- Screen state lives in ViewModels (`mutableStateOf`); database streams are `StateFlow`s.
- Animation specs come from `ui/theme/Motion.kt`.
- Bottom sheets use `LumeBottomSheet` (`core/ui`), never Material's `ModalBottomSheet` directly; a test fails the build otherwise.
- Sources are Kotlin engines in `data/source/` of `:feature:comics` or `:feature:novel`. New site families need an engine; a new site of a supported family only needs a JSON pack ([docs/ENGINES.md](docs/ENGINES.md)).

## Rules for changes

- **Strings:** every new UI string goes into both `res/values/strings.xml` (English) and `res/values-ru/strings.xml` (Russian). Use `plurals` for counted text.
- **Database:** Room changes are additive migrations only.
  - Add the migration to `LumeDatabase.MIGRATIONS`.
  - Commit the exported schema in `core/data/schemas/`.
  - Extend `core/data/src/sharedTest/java/com/lume/app/data/db/MigrationTest.kt`.
- **Tests:** pure logic gets JVM tests in the module that owns the code (`core/data/src/test/`, `feature/novel/src/test/`, …).
- **Style:** match the code around your change. Keep changes focused.
- **Docs:** update `docs/` when the behaviour of engines, packs or user-facing features changes.
- **Never commit** `local.properties`, signing keys, OAuth secrets, APKs, IDE files or personal scripts.

## Checks

Run what CI runs before opening a pull request:

```bash
./gradlew :core:model:test :core:common:testDebugUnitTest :core:data:testDebugUnitTest :core:ui:testDebugUnitTest :core:translate:testDebugUnitTest :feature:comics:testDebugUnitTest :feature:novel:testDebugUnitTest :app:assembleDebug :app:testFullDebugUnitTest :app:testMangaDebugUnitTest :app:testRanobeDebugUnitTest :app:lintFullDebug :app:lintMangaDebug :app:lintRanobeDebug
```

CI also runs lint on every module, the Roborazzi screenshot tests and the three release builds.

- On Windows the Room migration test is skipped on the JVM; CI (Linux) runs it. To run it locally, use a device or emulator: `./gradlew :core:data:connectedDebugAndroidTest`.
- To check the built-in sources against the live sites: `./gradlew :feature:comics:testDebugUnitTest -Plume.live` (add `-Plume.live.sources=mangadex,asura` for a subset). A weekly workflow does the same.
- Lint should report no issues.

## Commits and pull requests

- Branches: `feat/…`, `fix/…`, `docs/…`, `build/…`.
- Commit subjects: `feat: …`, `fix: …`, `refactor: …`, `build: …`, `docs: …`, `test: …`. One commit per sub-area.
- The pull request says what changed, how it was checked (device, emulator, tests) and what is not done yet. The template has the sections.
- CI must pass before merging.

## Releases

Maintainers only; the steps are in [docs/RELEASING.md](docs/RELEASING.md). In short: bump `lume.versionCode` and `lume.versionName` in `gradle.properties`, add `docs/releases/vX.Y.Z.md`, update the roadmap, then push a `vX.Y.Z` tag. The release workflow builds, signs and publishes `lume-vX.Y.Z-full.apk`, `-manga.apk` and `-ranobe.apk`, each with a `.sha256`. The Lume Translate add-on has its own `translate-vX.Y.Z` releases.

## License

By contributing, you agree that your contributions are licensed under the Apache License 2.0 (see [LICENSE](LICENSE)).

Please follow the [code of conduct](CODE_OF_CONDUCT.md).
