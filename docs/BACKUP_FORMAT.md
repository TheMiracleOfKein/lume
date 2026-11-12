# Lume backup format

A Lume backup is one UTF-8 JSON file (`LUME_backup_<yyyyMMdd_HHmmss>.json`, or `LUME_auto_…` for
scheduled backups). It holds the library and reading state. It never holds downloaded pages,
imported local files, sign-ins or tokens.

This page is the reference for the format and the promise behind it: **every backup an older Lume
made keeps loading in every newer Lume.** The code is `data/backup/BackupRepository.kt`
(`BackupPayload` and the `…Dto` classes). The test `BackupFormatTest` pins the field names and
loads one saved sample file per version from `app/src/test/resources/backups/`.

## Compatibility rules

1. **Fields are only added, never renamed, removed or retyped.** Every new field has a default, so
   files without it still load. A list defaults to empty, a number to `0`, a text to `null` or `""`.
2. **`version` goes up by one** when a release adds something a reader should know about. Lume
   doesn't refuse any version. The number says which parts the file can have.
3. **Unknown fields are ignored.** A file from a newer Lume loads in an older one; the older app
   just skips what it doesn't know.
4. **A restore merges, it doesn't wipe.** Rows are written over the existing ones by their key.
   Progress is kept when the phone's copy is newer. Page bookmarks and reading sessions aren't
   added twice when the same file is restored again. The database part runs in one transaction,
   so a broken file changes nothing.
5. **Files up to 64 MB** are read (a 600-title library is a few MB).
6. **A new version comes with a saved sample** `lume-vN.json` in the test resources. The old
   samples are never edited.

## Top level

| Field | Type | Since | Notes |
| --- | --- | --- | --- |
| `version` | int | 1 | Format version, currently **5**. Always written since v0.9.10; files from before that often leave it out, and a missing value reads as the current version. |
| `createdAt` | long | 1 | When the backup was made, in milliseconds since 1970 (UTC). Always written since v0.9.10. |
| `bookmarks` | list | 1 | Titles in the library. |
| `progress` | list | 1 | The "Continue" point of each title. |
| `categories` | list | 1 | Library categories. |
| `sourcePacks` | list | 1 | Installed JSON source packs. |
| `titlePrefs` | list | 1 | Per-title settings. |
| `trackerLinks` | list | 1 | Links between titles and tracker entries. |
| `chapterProgress` | list | 2 | Position inside each started chapter. |
| `pageBookmarks` | list | 2 | Page bookmarks. |
| `chapterRead` | list | 2 | Read marks. |
| `settings` | list | 3 | App settings. |
| `titleSources` | list | 4 | Other sources of a title. |
| `readingSessions` | list | 5 | Reading time for the statistics. |

Every list may be missing or empty. A title is always named by `sourceId` (Lume's source id, for
example `mangadex`) and `titleId` (the source's own id for the title). A chapter adds `chapterId`.
Times are milliseconds since 1970 (UTC). A `?` in the tables below marks a field that may be left
out; the default is shown.

## Lists

**`bookmarks`**: `sourceId`, `titleId`, `titleName`, `coverUrl?` (null), `typeLabel?` (null, e.g.
"Manga"), `createdAt?` (0), `categoryIdsJson?` (`"[]"`, a JSON array of category ids as a string),
`genresJson?` (`"[]"`, a JSON array of genre names as a string; added in v0.9.13 without a version
bump, older files load with no genres and Lume fills them in later).

**`progress`**: `sourceId`, `titleId`, `titleName`, `coverUrl?`, `typeLabel?`, `chapterId`,
`chapterName`, `chapterUrl`, `pageIndex?` (0), `pageCount?` (0), `updatedAt?` (0), `pageOffset?`
(0.0, 0–1 within the page, since version 2).

**`categories`**: `id?` (0), `name`, `sortOrder?` (0). Bookmarks point at categories by `id`.

**`sourcePacks`**: `id`, `json` (the pack as a JSON string), `installedAt?` (0). Packs whose id
matches a built-in source, or isn't a valid pack id, are skipped on restore.

**`titlePrefs`**: `sourceId`, `titleId`, `readerMode?` (null = app default), `readerTheme?`
(null), `gap?` (null), `chapterFilter?` (0), `chapterSortAsc?` (false), `branch?` (null, the
picked translation team), `showDuplicates?` (false), `autoDownload?` (false).

**`trackerLinks`**: `sourceId`, `titleId`, `tracker` (e.g. `anilist`, `myanimelist`, `kitsu`),
`remoteId`, `remoteUrl?`, `status?`, `score?`, `lastSync?` (0). Tokens are never included.

**`chapterProgress`**: `sourceId`, `titleId`, `chapterId`, `pageIndex?` (0), `pageOffset?` (0.0),
`pageCount?` (0), `completed?` (false), `pageAspectsJson?` (`"[]"`, page shapes as a JSON array
string), `updatedAt?` (0).

**`pageBookmarks`**: `sourceId`, `titleId`, `chapterId`, `chapterName`, `pageIndex`,
`pageOffset?` (0.0), `pageImageUrl?` (null), `note?` (null), `createdAt?` (0).

**`chapterRead`**: `sourceId`, `titleId`, `chapterId`, `readAt?` (0).

**`settings`**: `key`, `type`, `value`. `type` is one of `bool`, `int`, `long`, `double`, `float`,
`string` or `set`. `value` is always text; a `set` is a JSON array of strings. Unknown types and
values that don't parse are skipped. Private keys (tokens, secrets, passwords, sync settings, the
backup folder, tracker accounts) are never written and are ignored when found. Setting keys can
come and go between versions: a key Lume no longer knows is stored but has no effect.

**`titleSources`**: `sourceId`, `titleId` (the library title), `linkSourceId`, `linkTitleId`,
`linkTitleName`, `coverUrl?`, `state?` (`linked`), `priority?` (0), `addedAt?` (0).

**`readingSessions`**: `sourceId`, `titleId`, `titleName?` (""), `chapterId`, `startedAt`,
`durationMs?` (0), `pagesRead?` (0).

## Example

A small version 5 file:

```json
{
  "version": 5,
  "createdAt": 1762000000000,
  "bookmarks": [
    { "sourceId": "mangadex", "titleId": "t1", "titleName": "First Title", "categoryIdsJson": "[1]" }
  ],
  "categories": [ { "id": 1, "name": "Reading" } ],
  "chapterRead": [ { "sourceId": "mangadex", "titleId": "t1", "chapterId": "c1", "readAt": 1759000000000 } ],
  "settings": [ { "key": "reader_mode", "type": "int", "value": "2" } ]
}
```

## Version history

| Version | Lume | Added |
| --- | --- | --- |
| 1 | first releases | `bookmarks`, `progress`, `categories`, `sourcePacks`, `titlePrefs`, `trackerLinks` |
| 2 | exact resume | `chapterProgress`, `pageBookmarks`, `chapterRead`, `progress.pageOffset` |
| 3 | settings in backups | `settings` |
| 4 | several sources per title | `titleSources` |
| 5 | v0.9.6 | `readingSessions` |

Some optional fields inside the lists (for example `titlePrefs.branch`, `showDuplicates` and
`autoDownload`) came between version bumps. Following rule 1, older files without them load with
the defaults.

## Other backup formats

Lume also reads backups of other apps. It doesn't write them:
- **Mihon / Tachiyomi** (`.tachibk`, gzipped protobuf): titles of sources Lume has, categories,
  tracker links and read chapters (`MihonBackup.kt`).
- **Kotatsu** (`.bk.zip`): favourites and categories (`KotatsuBackup.kt`).

The file type is found from the content, not the name: a zip is Kotatsu, gzip is unpacked first,
and text that starts with `{` is a Lume backup.
