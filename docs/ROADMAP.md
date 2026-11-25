# Lume roadmap

What comes next for Lume, what is on hold and why, and the known problems. What has shipped is in the [release notes](releases/). The detailed plans for every version up to v1.13.0 are kept in the [roadmap archive](releases/ROADMAP_ARCHIVE.md).

Effort is rough: **S** = under a day, **M** = a few days, **L** = a week or more. Status: 🟡 partly done (the note says what's left), ⏸️ on hold (the note says why), no mark: planned. Order can change when a source breaks or a bug needs a patch release first.

## Principles

These hold for every release:

- **Everything stays on the phone.** No Lume account, no Lume server, no ads, no analytics. Network access goes only to the sources, trackers and services the user turned on ([PRIVACY.md](../PRIVACY.md)).
- **Sources are first-party engines.** No extension APKs ([why](MIHON_EXTENSIONS.md)); site families are added as JSON packs.
- **Nothing is lost on update.** Database changes are additive migrations with tests; backups from older versions keep loading.
- **English and Russian for every string.**
- **Checked before a release.** Each release goes through a device check of what changed ([reports](releases/checks/)).

## Where Lume is now

| Milestone | Versions | What it brought |
| --- | --- | --- |
| Foundation | 0.1–0.5 | Reader with exact resume, library, chapters, sources, downloads, backups, trackers |
| One title, many sources | 0.6.x | Linked sources, merged chapter lists, fallback reading, reading lists, similar titles |
| Sync and everyday reading | 0.7.x | Sync through a folder or WebDAV, reader controls, subscribed downloads, MangaLib bookmark import, app updates |
| Reader 2.0 | 0.8.x | Tall pages without memory problems, sharp zoom, crop and spreads, guided panel view, Coil 3 |
| Library and sources | 0.9.x | CBR/CB7/PDF, watched folders, OPDS/Komga/Kavita, InkStory, statistics, nearby sending, encrypted sign-ins, crash log |
| Stable, reading modes | 1.0 | Manga and Ranobe modes, separate builds, user guide, CI emulator and screenshot tests |
| Ranobe | 1.1–1.5 | Text reader, EPUB/TXT, RanobeLib, Royal Road, Scribble Hub, novel downloads, ranobe build, novel trackers |
| More novel sources | 1.14 | NovelFull and NOVGO: translated Chinese, Korean and Japanese novels and light novels |
| Listening, translation and AI | 1.6–1.12 | Read aloud, chapter translation (4 engines + offline add-on), name lookup and codex, AI story notes |
| Sources that move | 1.13 | Source addresses updated from `hosts.json` without an app update |

### Latest releases

| Version | Theme | Released |
| --- | --- | --- |
| [v1.14.0](releases/v1.14.0.md) | Translated novels | pending |
| [v1.13.0](releases/v1.13.0.md) | Source addresses without an update | 2026-11-24 |
| [v1.12.1](releases/v1.12.1.md) | Lib API address | 2026-11-24 |
| [v1.12.0](releases/v1.12.0.md) | AI story notes | 2026-11-24 |
| [v1.11.0](releases/v1.11.0.md) | Cheaper AI | 2026-11-23 |
| [v1.10.0](releases/v1.10.0.md) | AI settings | 2026-11-23 |
| [v1.9.0](releases/v1.9.0.md) | AI translation | 2026-11-22 |
| [v1.8.0](releases/v1.8.0.md) | Story lookup | 2026-11-21 |
| [v1.7.0](releases/v1.7.0.md) | Chapter translation | 2026-11-21 |

Every older version: [docs/releases/](releases/).

## Next

### Novels

- **Trackers made for novels** (M each), checked in this order:
  - **NovelUpdates**: the largest list of translated novels. No public API: search and series pages are read from the site, and the reading list needs the site's sign-in cookies (the way source logins work). Cloudflare may get in the way; check first.
  - **RanobeDB**: a light novel database with a public API; check what its user lists allow before planning sign-in.
  - **MangaUpdates novels**: its database has a "Novel" type, so it can serve Ranobe mode with the same account once its search keeps only novels.
  - Not planned: Kitsu (few novels, entries mixed with manga).
- 🟡 **Translated-novel sites** (M): Royal Road and Scribble Hub (1.4–1.5), NovelFull and NOVGO (1.14) are done. Still open: bigger light-novel sites such as NovelBin, NovelFire, FreeWebNovel and Ranobes, which answered plain requests with a Cloudflare block when checked. A selector-driven engine with one JSON pack per site can come with those.
- 🟡 **Translation** (1.7–1.11): still open are the chapter title row (it stays in the original), more offline language pairs than English → Russian, and offline speed measured on more arm64 phones.
- 🟡 **AI features** (1.9–1.12): a full check with a real key (device checks so far used a fake key and covered setup and error paths); "Sign in with OpenRouter" (OAuth PKCE), if OpenRouter accepts Lume's link scheme as a callback.
- **Codex in sync** (M): needs a sync collection with nested aliases, like the glossary's.
- Read aloud: a real headset's buttons with the screen off (keys injected on the emulator didn't arrive).

### Distribution and everyone

- **IzzyOnDroid** first; **F-Droid** when the build is reproducible (M).
- **Translations** of the app through a translation platform (for example Weblate), starting with Ukrainian, Spanish and Brazilian Portuguese (M, then contributors).
- 🟡 **Accessibility** (M): TalkBack labels, WCAG AA colours and growing layouts are done ([ACCESSIBILITY.md](ACCESSIBILITY.md)). Still open: a check on a real device at 200 % font size.
- Google Play stays out ([below](#out-of-scope)).

### Quality

- **Real tracker accounts** for MyAnimeList, Kitsu and MangaUpdates: sign in, update progress, edit status (S each).
- 🟡 **Benchmarks**: `StartupBenchmark` and `ScrollBenchmark` exist; their numbers on a real phone are still to be written down as a baseline (S).
- 🟡 **Sync on two devices**: the merge rules have unit tests; two emulators and a phone through Nextcloud and Syncthing, with offline edits on both sides, are not done yet ([DEVICE_TESTING.md](DEVICE_TESTING.md)).
- 🟡 **Nearby sending on two phones**: checked with JVM tests over real sockets, not yet on two devices.
- Source fixes from the weekly live check ([live-sources.yml](../.github/workflows/live-sources.yml)).

## On hold

| Item | Why |
| --- | --- |
| ⏸️ Google Drive as a sync backend | Needs an OAuth client; a cloud-drive app that syncs a folder works through "Folder" meanwhile |
| ⏸️ Nearby sending over Wi-Fi Direct or a local-only hotspot | Needs location / nearby-devices permissions and two real phones to check |
| ⏸️ Sending from a title's chapter list | The send screen lists every downloaded title instead |
| ⏸️ Com-X.life | The site checks every visitor with its own browser-fingerprint test, which the app's challenge flow doesn't recognise |
| ⏸️ "Sign in with Google" in site logins | Google blocks it in web views |
| ⏸️ Stronger sync key derivation (PBKDF2 600k) | Needs a new sync file format that older versions can't read |
| ⏸️ Links opening under the app lock | Waiting for unlock needs the lock state in the shell |
| ⏸️ Reading progress from MangaLib bookmarks | The API's progress fields don't line up with chapters in real answers |
| ⏸️ Rounded screen corners during the back gesture | Tried in v0.7.7, removed in v0.7.8: it looked different from screen to screen |

## Known problems

| Problem | Plan |
| --- | --- |
| MangaRead and ToonGod often show Cloudflare checks; ReManga and MangaBuff block some regions | The in-app browser handles checks; region blocks can't be fixed in the app |
| HentaiLib needs a signed-in account to read | Works as designed; the reader says so |
| YaoiLib title pages answer 404 without a sign-in, while its catalogue and chapter lists still load | Check with a signed-in account and then say so on the title page. The site now lives at slashlib.me (SlashLib, added in v1.5.3); YaoiLib can go once its library entries can move over |
| When Home's source needs a Cloudflare check, the error fills Home and hides Continue reading | Show the error in the feed's own section and keep Continue reading above it |
| Room 6→7 migration can't be tested (no v6 schema was exported) | Accepted; every later migration is tested |

## Ideas, not planned yet

- Android TV and Chromebook: D-pad and keyboard navigation in the reader.
- A community pack catalogue with its own review rules.

## Out of scope

- Loading Mihon / Tachiyomi extension APKs or any downloaded code ([MIHON_EXTENSIONS.md](MIHON_EXTENSIONS.md)).
- A Lume account or server, ads, analytics, telemetry.
- Hosting or mirroring content.
- Google Play: its policies on third-party content don't fit a reader with built-in sources.
