# Releasing Lume

Releases are signed with one key, so every APK installs over the previous one. A release can be
built on the maintainer's computer (the usual path) or by the optional **Release** workflow
([release.yml](../.github/workflows/release.yml)) from a tag, with the key kept in repository
secrets. Both paths make the same thing: `lume-vX.Y.Z.apk` on a GitHub release titled
`Lume X.Y.Z`, with the notes from `docs/releases/vX.Y.Z.md`.

## Before a release

1. `versionCode` +1 and `versionName` in `app/build.gradle.kts`.
2. Release notes in `docs/releases/vX.Y.Z.md`, roadmap marks in [ROADMAP.md](ROADMAP.md).
3. The device check for what changed ([DEVICE_TESTING.md](DEVICE_TESTING.md)).
4. Merge to `main`, then tag the merge commit: `git tag vX.Y.Z <commit> && git push origin vX.Y.Z`.

## Local build

Signing reads `LUME_STORE_FILE`, `LUME_STORE_PASSWORD`, `LUME_KEY_ALIAS` and
`LUME_KEY_PASSWORD` from the user's `~/.gradle/gradle.properties`; the tracker app ids come from
`local.properties` (both stay out of the repository). Without all four signing values the release
build stays unsigned, as on CI.

```bash
./gradlew :app:clean :app:assembleRelease
apksigner verify --print-certs -v app/build/outputs/apk/release/app-release.apk
```

The signer must be `CN=Lume App` with SHA-256
`b1594cec70301bfd0f66d73d82e9bdcea6355b8df8d2abe50e1fc022629bd565`.

## Release workflow (optional)

### One-time setup

In the repository: Settings → Secrets and variables → Actions.

| Secret | Value |
| --- | --- |
| `LUME_KEYSTORE_BASE64` | The keystore file as base64 (`base64 -w0 <keystore>`) |
| `LUME_STORE_PASSWORD` | Keystore password |
| `LUME_KEY_ALIAS` | Key alias |
| `LUME_KEY_PASSWORD` | Key password |
| `LUME_ANILIST_CLIENT_ID`, `LUME_ANILIST_CLIENT_SECRET` | AniList app (optional) |
| `LUME_SHIKIMORI_CLIENT_ID`, `LUME_SHIKIMORI_CLIENT_SECRET` | Shikimori app (optional) |
| `LUME_MAL_CLIENT_ID` | MyAnimeList app (optional) |

Without the tracker ids the build works, but the matching sign-ins are off in that APK; the
workflow warns about it.

| Variable | Effect |
| --- | --- |
| `LUME_RELEASE_FROM_CI` | `true`: every pushed `v*` tag starts the workflow. Unset: only a manual run does. |
| `LUME_CERT_SHA256` | Only if the signing key ever changes: the new certificate's SHA-256. |

### Running it

- **By hand:** Actions → Release → Run workflow, with the tag (it must already be pushed).
- **On every tag:** set `LUME_RELEASE_FROM_CI` to `true`, then pushing the tag is the release.

The workflow:
1. checks the tag (`vX.Y.Z`), the secrets, and that `versionName` matches the tag;
2. builds `:app:assembleRelease` with the key from the secrets, then deletes the keystore file;
3. checks the signature and certificate, the version in the APK and the baseline profile;
4. keeps `lume-vX.Y.Z.apk` and `lume-vX.Y.Z.apk.sha256` as a workflow artifact;
5. creates the GitHub release with both files and the notes, **only if the tag has no release
   yet**. An existing release (for example one published from the local build) is never changed.

A manual run for an older tag marks that release as the latest; edit the releases afterwards if
that isn't wanted.

### Keeping the key safe

- The keystore and passwords exist only in the secrets and, during a run, in the runner's
  temporary folder. GitHub hides secret values in logs.
- Workflows from forks get no secrets, so a pull request can't sign anything.
- Keep the offline copy of the keystore: a lost key means users must uninstall to update.
