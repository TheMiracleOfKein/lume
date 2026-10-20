# Device testing checklist

Releases v0.3 to v0.6 were checked with builds, unit tests and an emulator. The items below still need real phones (and, for trackers, real accounts). Tick them off here as they are checked, with the phone and Lume version.

Use the signed release APK, installed over the previous release, so the database migrations and R8 build are covered too.

## Reader and resume (v0.3)

- [ ] A very tall webtoon page: leave in the middle, reopen, same spot.
- [ ] Rotate the phone while reading: same spot.
- [ ] Android closes Lume in the background (or "Don't keep activities" in developer options): same spot after reopening.
- [ ] Read on into the next chapter, go back: each chapter keeps its own spot.
- [ ] Page bookmarks: add with a note, find on the title screen, jump back.
- [ ] A chapter counts as read only when finished; the tracker updates at the same moment.
- [ ] Motion: bottom bar hides and shows without jumps; side menu follows a swipe; back gesture follows the finger.

## Library and updates (v0.3, v0.4)

- [ ] Background library check finds new chapters; the notification asks for permission and opens the library.
- [ ] Updates screen: "Update now" and pull to refresh.
- [ ] Library grid, compact and list views with unread, downloaded and source counts.
- [ ] Select several titles: categories, mark read/unread, download unread, remove.

## Downloads and local files (v0.5)

- [ ] Download notification: progress, pause, resume; keeps going in the background and after Android closes the app.
- [ ] Save as CBZ, then read the CBZ chapter offline.
- [ ] Download to an SD card.
- [ ] Download new chapters automatically; delete after reading.
- [ ] Import a folder with `ComicInfo.xml` and a cover.

## Backups and trackers (v0.5)

- [ ] Import a real Mihon / Tachiyomi backup (`.tachibk`): titles, categories, tracker links, read chapters.
- [ ] Automatic backups to a chosen folder, with rotation.
- [ ] Sign in to MyAnimeList, edit status and score, finish a chapter: progress updates.
- [ ] Same for Kitsu.
- [ ] Same for MangaUpdates.
- [ ] Shikimori stays signed in after its token expires.

## App (v0.5)

- [ ] App lock: system prompt on start and after a while in the background.
- [ ] "Hide content in recent apps": blank thumbnail, no screenshots.
- [ ] Tablet (600 dp or wider): navigation rail.
- [ ] Material You colours and the pure-black theme.

## One title, many sources (v0.6)

- [ ] A chapter whose source really fails (site check, region block) loads from a linked source; the spot and read mark stay.
- [ ] Downloads of linked chapters.
- [ ] The update notification names the source that had the chapter first.
- [ ] "Find on other sources" doesn't suggest spin-offs as the same title.
- [ ] Widget and launcher shortcuts open the reader at the right spot; with app lock on they show nothing.

## v0.6.1

- [ ] YaoiLib: a chapter opens and its pages load.
- [ ] HentaiLib: without sign-in a chapter says to sign in; after signing in under Sources it opens.
- [ ] Library: the sources badge shows on titles with linked sources and can be turned off.

- [ ] Launcher icon is sharp and correctly shaped on several launchers; themed icon on Android 13+.
- [ ] Download and library-update notifications show the Lume mark in the status bar, not a white square.
- [ ] Android backup (Settings → System → Backup): Lume's backup works and no longer includes downloads.

## v0.6.2

- [ ] Updating from v0.6.1 while signed in to trackers keeps you signed in (tokens move to `lume_private`).
- [ ] Restoring from Android's cloud backup on a new phone brings the settings back, without tracker sign-ins.
- [ ] Settings search in Russian: "тёмная", "бэкап", "трафик" find their settings; a result opens its section and highlights the row.

## v0.6.3

- [ ] Library → Share → send the list to another phone through a messenger; on that phone, opening the file in Lume shows the list and adds the titles.
- [ ] Settings → Backup and restore → Open a reading list picks a saved list from Downloads.
- [ ] A list with a title from a source the phone doesn't have: found through its web link, or marked "Lume doesn't have this source".
- [ ] A shared site link opens in the Lume window you are looking at, also when Lume was already open.

## v0.6.4

- [ ] Title screen: "Similar titles" shows covers for a well-known title (English and Russian sources); a tap opens global search with that name.
- [ ] A title from a Russian source with only a Russian name still finds its recommendations (AniList knows many Russian names), or shows no row.
- [ ] Signed in to MyAnimeList: a title AniList doesn't know shows "Recommended on MyAnimeList".
- [ ] Settings → Sources and search → Similar titles off: the row is gone and no request goes to AniList (check with a proxy or the network log).

## v0.6.6

- [ ] Search → filters on MangaDex, MangaLib and a Madara pack: the panel opens at once, then "Loading the site's filters…" turns into genre and tag groups.
- [ ] Include and exclude a genre (+, then struck through): the results change; "Has all / has any" changes them again.
- [ ] A long group (MangaLib tags) starts folded, shows what is chosen when folded, and the find field narrows the chips.
- [ ] ReManga or Asura with search text: the panel says filters need an empty search field; without text the filters apply.
- [ ] Catalogue → Popular: the section chips switch lists and keep each list's scroll; changing the active source shows that source's chips and titles, never the old source's.
- [ ] Title screen: author, artist and "type · year · ★ score" for a MangaDex, a MangaLib and an Asura title; long author lists end with "…".
- [ ] All of the above in Russian.

## v0.7.0

Sync needs two devices (two emulators work). Use the same folder or server on both.

- [ ] Settings → Sync → Folder: pick a Syncthing folder (or a cloud drive app's folder) on phone A with a library, then the same folder on phone B. After "Sync now" on both, B shows A's titles, categories, read marks and the spot in each chapter.
- [ ] The same through WebDAV (Nextcloud): a wrong password shows "sign-in refused"; a folder that doesn't exist is created.
- [ ] Read further on B, sync both: A continues at B's spot. Mark a chapter unread on A, sync both: it is unread on B too.
- [ ] Remove a title on A, sync both: it is gone from B. Add it back on B: it comes back on A.
- [ ] Offline edits on both sides (airplane mode), then sync both: both sets of changes are kept.
- [ ] Passphrase on A only: B says another device encrypts its file. The same passphrase on B: sync works. A different one: "the passphrase doesn't open another device's file".
- [ ] Clear Lume's data on B and turn sync on again: B gets the library back and A loses nothing.
- [ ] Background sync every hour runs (the status line moves on) without opening the app.
- [ ] Reader: the page slider jumps to a page in webtoon, left-to-right and right-to-left modes (the slider runs right to left there).
- [ ] Orientation lock portrait and landscape hold while the phone turns; leaving the reader lets the app turn again.
- [ ] Webtoon auto-scroll: play scrolls steadily, the speed setting changes it, a drag stops it, the end of the chapter stops it.
- [ ] Webtoon tap zones on: top third scrolls back, bottom third scrolls on, middle shows the controls. Off: any tap shows the controls.
- [ ] Sepia, grey and warm filters, e-ink mode (no animated page turns), own brightness (back to the system brightness after leaving the reader).
- [ ] Page menu → Share page sends the image with the chapter link; Save page puts it in Pictures/Lume (Android 9: a save dialog).
- [ ] All of the above in Russian.

## Performance

- [ ] Run `StartupBenchmark` on a real phone (`./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`) and note the startup time with and without the baseline profile.
