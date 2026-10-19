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

## Since v0.6.0 (next release)

- [ ] YaoiLib: a chapter opens and its pages load.
- [ ] HentaiLib: without sign-in a chapter says to sign in; after signing in under Sources it opens.
- [ ] Library: the sources badge shows on titles with linked sources and can be turned off.

- [ ] Launcher icon is sharp and correctly shaped on several launchers; themed icon on Android 13+.
- [ ] Download and library-update notifications show the Lume mark in the status bar, not a white square.
- [ ] Android backup (Settings → System → Backup): Lume's backup works and no longer includes downloads.

## Performance

- [ ] Run `StartupBenchmark` on a real phone (`./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`) and note the startup time with and without the baseline profile.
