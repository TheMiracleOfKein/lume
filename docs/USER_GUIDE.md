# Lume user guide

This guide shows how to use Lume, from the first launch to every setting. Names in **bold** are the words you see on the screen (English). If something here doesn't match your app, check [About](#about-and-licenses) for the version and the [release notes](releases/).

Contents:

1. [Getting started](#getting-started)
2. [Home](#home)
3. [Browsing and searching sources](#browsing-and-searching-sources)
4. [Sources and packs](#sources-and-packs)
5. [Title screen](#title-screen)
6. [Reading](#reading)
7. [Library](#library)
8. [Downloads](#downloads)
9. [Sync](#sync)
10. [Backups](#backups)
11. [Trackers](#trackers)
12. [Privacy and security](#privacy-and-security)
13. [Crash log and reporting bugs](#crash-log-and-reporting-bugs)
14. [Troubleshooting](#troubleshooting)
15. [FAQ](#faq)
16. [Settings reference](#settings-reference)

## Getting started

### Install

1. Download the APK from the [latest release](https://github.com/TheMiracleOfKein/lume/releases/latest). Lume needs Android 8.0 or newer.
2. Open the file. If Android blocks the install, allow installs from your browser or file manager, then open the file again.
3. To update, install the new APK over the old one. Your library, progress and downloads stay.

The [README](../README.md) shows how to check the signature of a download.

### First launch

Lume opens on **Home**. There is no account and no sign-up. Nothing is sent anywhere unless you sign in to a tracker or turn on sync.

The bar at the bottom has five places:

- **Home**: where you stopped and what is new.
- **Library**: the titles you saved.
- **Catalog**: browse and search a source.
- **Updates**: new chapters from your library.
- **Menu**: theme, **Incognito**, **Search**, **Source**, **Settings**, **Downloads**, **History** and **About and licenses**.

A good first run:

1. Open **Catalog** and pick a source (see [Browsing and searching sources](#browsing-and-searching-sources)).
2. Open a title and tap the bookmark icon to add it to the library.
3. Tap **Read**.

On a tablet the bottom bar becomes a navigation rail.

The first time you turn on **Auto-check library**, Android 13 or newer asks to allow notifications. Allow them if you want to hear about new chapters.

## Home

Home shows:

- **Continue reading**: the titles you read last. Tap one to open the exact spot where you stopped.
- **Currently reading** and **New releases**: feeds from your sources.
- When you have no connection, **Offline · downloaded titles** shows what you have downloaded.

Pull down to refresh. If a feed fails you see **Couldn't refresh the feed**.

You can remove one title from Continue reading with **Remove from history**. Its chapters keep their read marks and page positions. **Clear history** removes every Continue reading card and saved position, so Lume asks you first.

There is also a **Continue reading** widget for the home screen of your phone, and launcher shortcuts (long-press the Lume icon). If you turned on **Hide in recent apps**, the widget asks you to open Lume instead of showing titles.

## Browsing and searching sources

Open **Catalog** to browse the active source. The **Source** entry in the menu switches to another one.

- The tabs are **Popular**, **Updates** and **Library**. **Library** lists only this source's titles that you already saved.
- Some sources also show sections such as **New titles**, **Top rated**, **Completed** or **Most viewed**.
- Open **Filters** to filter by things like **Genres**, **Tags**, **Status**, **Type**, **Content rating** or year, and to change the sort. Tap a genre or tag once to include it and twice to exclude it. Tap **Apply** to use the filters or **Reset** to clear them.
- The search field at the top searches this source only, together with the filters (the tune button). Results replace the tabs; **X** or **Back** returns to them.
- Some sites can't filter and search at the same time: Lume tells you to clear the search field.
- Titles whose name matches what you typed come first; titles that only mention it in the description come after.

**Global search** asks many sources at once:

- Open **Menu → Search**, or switch the search between **Current source** and **All sources**.
- Results are grouped per source with a count. A source that is slow, down or blocked shows a short reason (for example **No answer in 15 s**) and doesn't stop the others.
- Your last searches appear under **Recent searches**. Remove one, or tap **Clear**.
- 18+ sources are included by default. Turn off **Include 18+ sources in global search** to leave them out.
- Disabled sources are left out of global search too.

You can also open a title link from your browser, or share it to Lume.

## Sources and packs

Lume has built-in sources: MangaDex, MangaKatana, Asura Scans, MangaRead, HiveToons, Vortex Scans, MangaLib, YaoiLib, HentaiLib, ReManga, MangaBuff and **Local files**. The full list is in the [README](../README.md#built-in-sources).

Manage them in **Settings → Sources and search → Sources** (or **Menu → Source**).

On each source's row:

- Tap the key icon to sign in or sign out (for sites that have accounts, such as MangaLib).
- The three-dot menu has **Browse**, **Settings**, **Import bookmarks** (MangaLib family), **Pin to top** or **Unpin**, **Disable** or **Enable**, and **Remove** (for packs and servers you added).
- A line under a source shows its last problem, for example when a site asked for a check.
- **Disable** hides a source from global search. The active source can't be disabled.

### Source settings

Some sources have their own settings. MangaDex has **Chapter languages** (English and Russian by default), **Content rating** (Safe, Suggestive and Erotica by default) and **Data saver: smaller, lower-quality pages** (off). Sources without settings say **This source has no settings**.

### JSON packs

Many sites share the same engine. A *pack* is a small JSON file that adds one more site of a family: Madara, LibSocial or HiveToons.

- **Pack catalogue**: tap **Check packs and updates**, then **Install** a pack. **Update all** updates every installed pack.
- **Import pack from URL**: paste a link to a pack file and tap the button.
- Packs only clone a family. They don't contain code.

How packs are written is in [packs/README.md](packs/README.md) and [ENGINES.md](ENGINES.md).

### Mihon and Tachiyomi extensions

Lume does not load Mihon or Tachiyomi extension APKs, so you can't install them. The reasons are in [MIHON_EXTENSIONS.md](MIHON_EXTENSIONS.md). What you can do:

- Import a Mihon or Tachiyomi backup (`.tachibk`). Titles from sources Lume has are added, with categories, tracker links and read chapters. See [Backups](#backups).
- Ask for a new built-in source or pack by opening an issue on GitHub.

### Self-hosted servers: OPDS, Komga and Kavita

Tap **Add server** in the Sources screen. Choose a **Type** (OPDS, Komga or Kavita) and fill in:

- **Name** (optional).
- **Address**, for example `https://komga.example.com`. A plain `http://` address works, but Lume warns you that it isn't encrypted. Use it only on a network you trust.
- **Sign-in**: **User name and password**, or **API key**. Leave it empty if the server needs none. Credentials are stored encrypted on your phone. Kavita always needs a sign-in.
- For some servers, **Only this library (id, optional)**.

Reading progress is sent back to Komga and Kavita. To change a server later, use **Edit server** in its three-dot menu. If you change the address, enter the password again: Lume never sends a saved password to a different address.

### Local files and watched folders

The **Local files** source holds comics from your phone.

- **Settings → Library and updates → Import a folder from the device** copies a folder into Lume. A folder can hold one series per subfolder, chapter folders, or `.cbz`, `.cbr`, `.cb7`, `.zip`, `.rar`, `.7z` and `.pdf` files. `cover.jpg`, `ComicInfo.xml` and `details.json` are read too.
- **Open local titles** takes you to them.
- **Watched folders** are re-scanned for new files and changed files, and new chapters are copied in with a notification. Add one with **Add a watched folder**, scan at once with **Scan now**, and choose how often with **Scan every**. If Android takes back access to a folder, tap **Reconnect**.
- **Clean temporary files** frees space used by unpacked archives.

Lume can't open RAR5 archives or password-protected PDFs. Repack them as CBZ.

## Title screen

Tap a title to open it. At the top are buttons to open it in the browser, share it, add or remove it from the library (bookmark icon) and the bell.

- **The bell** (**Check for new chapters**) subscribes the title. Up to 20 titles can have the bell. With **Download new chapters** set to **Subscribed**, only these titles download automatically. If no title has the bell, Updates checks the 20 titles you read last.
- **Read** or **Continue** opens the next unread chapter, or the spot where you stopped.
- **Chapter list** opens the chapters. Tap one to read. Long-press to select several, then use **Mark as read**, **Mark as unread**, **Mark previous as read**, **Download** or **Delete download**. The top bar has a download menu (**Download unread**, **Next N unread**, **Delete downloaded chapters**), a sort button (**Oldest first** or **Newest first**) and **Filter and sort** (**Unread**, **Read**, **With page bookmarks**). When a source has several translation teams, pick one or choose **Show all copies**.
- **Description**, status, author, artist and score are shown when the source has them. Genres open a catalogue filtered by that genre.
- **Bookmarks** lists your page bookmarks with the panel you marked.

### Linking several sources

One title can come from several sources.

- When you add a title, Lume looks for it on your other sources and shows **Also on …**. Tap **Link** to link them, or **Not now**. Turn this off with **Find titles on other sources**.
- If your library already has the same title, Lume asks **Already in the library?** and offers **Link to it**, **Open it** or **Add anyway**.
- Open **Sources** on the title to see the **Main source**, the **Linked sources** and the ones **Found for you**. Use **Find on other sources** to search again.
- Chapters the main source doesn't have come from the linked sources, in the order shown. When a chapter fails to load, Lume reads it from another source and tells you.
- **Make main** and **Move here** change the main source. Progress, page bookmarks, read chapters, categories, tracker links and downloads move with the title. **Unlink** removes a source; its chapters leave the title.

### Similar titles

**Similar titles** shows recommendations from AniList (or MyAnimeList, if you are signed in). Tap one to search your sources for it. Lume sends the title's name to them. Turn this off with **Similar titles** in **Sources and search**.

### Trackers on the title

**Trackers** opens the tracker panel. For each signed-in tracker you can search and link the title, enter an ID by hand (**Link ID**), **Sync**, **Edit** the entry (status, score, chapters read, start and finish dates), or **Unlink**. See [Trackers](#trackers).

## Reading

### Reader modes

Choose a **Reading mode** in the reader settings:

- **Webtoon**: one long vertical strip. This is the default.
- **LTR**: pages, left to right.
- **RTL**: pages, right to left (manga).

You can change the mode for one title only with **Save for this title**.

### Controls

Tap the middle of the screen to show or hide the controls.

- Top bar: back, the title, a button that turns **Guided panel view** on or off, add to or remove from library, **This page** (**Share page**, **Save page to Pictures**) and the settings button.
- Bottom bar: a page slider, previous and next chapter, the chapter list, **Bookmark this spot** (hold for the list) and the settings button. In webtoon mode there is also **Start auto-scroll** / **Stop auto-scroll**.
- Paged modes: tap the left or right third to turn pages (**Tap left / right edge to turn pages**). Volume keys can turn pages too.
- Webtoon mode: you can scroll with taps if you turn on **Tap zones in webtoon mode**: the top third goes back, the bottom third goes on.

By default the reader is fullscreen: scrolling hides the controls and the system bars, and a tap brings them back.

Lume remembers your place inside the page, per chapter, after rotating the phone or when Android closes the app. **Seamless chapters** continue into the next chapter without leaving the reader, with a divider between them.

### Zoom

Pinch to zoom, or double-tap to zoom in and double-tap again to zoom out (**Double-tap zoom**). The part you zoom into is decoded again at full detail, even on very tall strips.

### Guided panel view

Turn on **Guided panel view** (paged modes only). A tap or page turn moves from panel to panel, zoomed in, in reading order. Each page starts whole. Panels are found on your phone, with no model and no network. You can switch it on and off from the top bar while reading. Crop borders isn't used in this view.

### Cropping and spreads

These work in paged modes.

- **Crop borders** cuts off plain white or black margins.
- **Split double pages**: in portrait, a page wider than tall is shown as two halves, in reading order.
- **Two pages side by side**: **Off**, **Landscape** (landscape and wide screens) or **Always**. **First page alone** keeps the first page of a chapter (often a cover) on its own, so the pairs match a printed book.

### Page bookmarks

Tap **Bookmark this spot** to pin a page (in webtoon mode, the exact spot). **Add note** lets you write what happens there. Hold the button to see the list. Bookmarks also appear on the title screen with the panel you marked. Swipe a bookmark away, or use its bin, to delete it; **Undo** brings it back.

### Looks and comfort

The reader settings panel (the tune icon) also has **Reader theme**, **Dim pages**, **Own brightness while reading**, **Colour filter**, **E-ink mode**, **Screen orientation**, **Keep screen on** and more. All are described in the [Settings reference](#reader-settings-panel).

## Library

The **Library** holds the titles you saved.

- Search with **Search library**.
- **Sort, filter and display** opens a sheet with three tabs: **Filter**, **Sort** and **Display**. See the [reference](#library-options-sheet).
- Each title can show chapters read of total, the downloaded count and the number of sources.
- **Long-press** a title to select several. The bar at the bottom has **Categories**, **Mark as read**, **Download unread**, **Remove**, and a more menu with **Mark as unread**, **Bell on or off** and **Share**. Removing from the library keeps downloads and reading progress. The top bar can select all or invert the selection.

### Categories

Categories are tabs in the Library. Open **Settings → Library and updates → Categories** (or the edit button in the Library) to add, rename, reorder and delete them. Choose a **Default category** for new titles. Deleting a category keeps its titles. Titles with no category are under **Uncategorized**. Library tabs include **All**.

### Updates

**Updates** lists new chapters from the whole library, grouped by day (**Today**, **Yesterday**, …). Each entry shows which source published it first. Pull down, or tap **Update now**, to check. A line shows when the last check ran and how many titles failed.

Background checks are set in [Library and updates](#library-and-updates). Lume checks titles with the bell first (up to 20); if none has a bell, it checks the 20 you read last.

### Statistics

**Settings → Library and updates → Reading statistics** shows time, pages and chapters you have read, by month and by title.

### Reading lists

A reading list is a small file with names, sources and links of titles (not your progress).

- Share one: tap the share button in the Library (or select titles first), give it a **List name**, then **Share** or **Save as file**.
- Open one: **Settings → Backup and restore → Open a reading list…**. Pick a **category** if you like and tap **Add … to the library**. Titles from sources Lume doesn't have are skipped and counted.

## Downloads

Open **Menu → Downloads** to see the queue.

- Downloads run in the background with a notification. You can **Pause**, **Resume**, **Cancel all**, **Retry all** and **Change order** (**Download first**, **Move up**, **Move down**). Failed downloads say **Tap Retry to try again**.
- The screen shows how much space your downloads use.
- Download chapters from a title (single chapters, a selection, **Download unread**, **Next N unread**).
- Downloaded chapters open offline. With no connection, Home and Library show your downloads.

### Storage

**Settings → Downloads → Download folder** picks where new downloads go: **Internal storage**, an **SD card** or another storage, or a folder you choose with **Choose a folder…** (any folder, an SD card too; chapters go into a Lume folder inside it). Each place shows its free space. New downloads go to the chosen place; earlier ones stay where they are. If Android takes back access to a chosen folder, new downloads use internal storage until you **Reconnect**.

**Save as CBZ** packs each chapter into one archive with `ComicInfo.xml`, which other readers open too.

**Restore downloads from disk** finds downloaded chapters again, for example after a reinstall.

### Subscribed downloads

Set **Download new chapters** to **Subscribed** and ring the bell on a title: only those titles download new chapters during library checks. **All titles** downloads for everything in the library. **Off** (default) downloads nothing by itself. **Delete after reading** removes a chapter's download once you finish it and close the reader.

### Sending to a nearby device

You can send downloaded chapters to another phone with Lume, without a server.

1. Both phones join the same Wi-Fi.
2. On the sender, open **Downloads** and choose **Send to a nearby device**. Select chapters. **Send read marks too** is optional.
3. On the other phone, open **Downloads → Receive from a nearby device** and pick the sender. If the sender isn't listed, type the address shown on the sender's screen.
4. Both phones show the same code. Check it matches, then tap **Accept** on the receiver.

Titles go to the library and chapters to Downloads. Nothing is saved until you accept. Lume accepts connections only while the screen is open. If the link drops, connect again: chapters that arrived aren't sent twice. Chapters from sources the other phone doesn't have are skipped.

## Sync

Sync keeps the library, reading spots and read marks the same on your phone and your tablet. There is no Lume account. Each device writes a small file to a place you choose and reads the files of the others.

Open **Settings → Sync**.

1. Under **Sync through**, choose **Folder** or **WebDAV**.
   - **Folder**: pick a folder that another app keeps the same on every device, for example Syncthing or a cloud-drive app. Pick the same folder on each device.
   - **WebDAV**: enter the **Folder address**, **User name** and **Password or app password** (Nextcloud, ownCloud, a NAS or any WebDAV folder). Use the same address everywhere. Lume creates the folder if it doesn't exist.
2. Optionally set a **Passphrase**. It encrypts the sync files with AES-256, and every device needs the same one. Without it, anyone who can open the folder can read the files.
3. Choose **Sync in the background**: **Manual**, or every 1, 6, 12 or 24 hours. Lume also syncs when it starts.
4. Tap **Sync now** to sync at once. The status line shows the last sync and how many other devices were read.

What syncs: the library and categories, reading spots, read marks, page bookmarks, linked sources, tracker links, per-title reader settings and source packs. What doesn't: sign-ins, downloads and app settings. The newest change wins; for a chapter, the furthest spot wins.

If you forget the passphrase, set a new one on every device. Nothing on the devices themselves is lost.

## Backups

A backup holds the library, categories, progress, bookmarks and settings. Sign-ins are not included. The format is documented in [BACKUP_FORMAT.md](BACKUP_FORMAT.md), and older backups keep loading.

Open **Settings → Backup and restore**:

- **Create backup** saves a backup inside the app.
- **Save a backup to…** saves the same backup wherever you choose (Downloads, a cloud folder).
- **Restore** restores the newest backup saved inside the app.
- **Restore from a file…** restores a file you pick: a Lume backup, a Mihon or Tachiyomi backup (`.tachibk`) or a Kotatsu backup (`.bk.zip`).
- **Automatic backups**: choose **Off**, 1, 3 or 7 days. Then choose **Keep the newest** 3, 5 or 10 backups. **Also copy to a folder…** copies each one to a folder you pick; **Stop copying to the folder** turns that off.

### Importing from other apps

- **Mihon or Tachiyomi** (`.tachibk`): Lume adds titles from sources it has, with categories, tracker links and read chapters, and shows a progress bar while it reads chapter lists. Titles from sources Lume doesn't have are skipped, and the result names them.
- **Kotatsu** (`.bk.zip`): titles are added. Reading history isn't imported.
- **Tracker lists**: see [Trackers](#trackers).
- **MangaLib bookmarks**: in Sources, open the three-dot menu of MangaLib, YaoiLib or HentaiLib (signed in) and tap **Import bookmarks**. Lume loads your bookmarks, adds the titles, turns bookmark folders into categories and then reads progress. You can leave the screen while it runs.

## Trackers

Trackers keep your reading list and progress on sites like AniList. Lume updates them when you finish a chapter.

Supported: AniList, Shikimori, MyAnimeList, Kitsu and MangaUpdates.

- Sign in under **Settings → Trackers**. AniList, Shikimori and MyAnimeList open a sign-in page. Kitsu and MangaUpdates ask for a user name or e-mail and a password; Lume keeps only the tracker's sign-in token.
- **Link trackers automatically** links a title when you add it, if a signed-in tracker finds the exact same name. You can always link by hand on the title screen.
- **Import reading list** (AniList and MyAnimeList) reads your Reading list, searches your sources for each title and adds what it finds, with your progress. It runs in the background and ends with the counts for added, already in library and not found.
- AniList, Shikimori and MyAnimeList sign-in works only in builds that include the tracker app keys (see the [README](../README.md#tracker-keys-optional)).

A signed-in tracker can also sync progress both ways from the title's tracker panel with **Sync**.

## Privacy and security

- **Incognito mode**: reading leaves no history, progress or read marks. You can switch it from the menu.
- **App lock**: Lume asks for your phone's screen lock (fingerprint, PIN, pattern) when it opens. If your phone has no screen lock, you are asked to set one first.
- **Hide in recent apps**: a blank preview in the app switcher, and screenshots are blocked.
- **Encrypted sign-ins**: tracker tokens, tokens of MangaLib-family sites, server credentials and the sync WebDAV password and passphrase are encrypted with a key held by the Android Keystore. They stay out of backups and cloud backup. Cookies of sites you signed in to in the built-in browser are stored by Android's WebView and can't be encrypted by Lume. **Clear cookies** removes them.
- **DNS over HTTPS** helps when your provider blocks sites by DNS.
- Lume has no ads and no analytics. See [PRIVACY.md](../PRIVACY.md).

## Crash log and reporting bugs

If Lume closes because of a crash, the details are saved on your phone. Nothing is sent by itself.

- **Settings → About and licenses → Share log** hands the crashes, the recent errors, the Android version and the phone model to an app you choose (e-mail, a bug report). **Clear crash log** deletes them.
- **Error log** (also in About and on the Sources screen) lists recent errors of sources with their full text. Tap an entry for details, then **Copy**, **Copy all**, **Share** or **Clear**.

To report a bug, open an issue on [GitHub](https://github.com/TheMiracleOfKein/lume/issues) and attach the shared log. Say what you did, the title and the source. Remove anything you don't want to share first.

## Troubleshooting

### Cloudflare or DDoS-Guard checks

A site asks you to prove you are human. Lume shows **Cloudflare check**: wait until the source site itself shows (not the check page), then tap **Continue**. If it keeps coming back:

- Open a title on that source so the check can pass in the built-in browser.
- Try **Clear cookies** (**Network and storage**) and pass the check again.
- Try a different network, or turn on **DNS over HTTPS**.

### Region blocks

**… is not available in your region**, or **Access denied … The site may block your region, or the content needs a login**. Options: turn on **DNS over HTTPS**, use a network that isn't blocked, or read the title from another source (link one on the title screen).

### Sources that need sign-in

**… shows chapters only to signed-in users. Sign in under Sources → …** Open the source row, tap the key icon and log in on the site. E-mail and password work best; Google sign-in may not work inside apps. If the site still shows the old account, sign out on the site first. HentaiLib shows chapters only to signed-in users. A server (Komga, Kavita, OPDS) that says it **needs a sign-in** needs **Edit server** and the user name and password or API key.

### Black pages and early-access chapters

- A black or empty page can be a site check page or a page that didn't load (**Page failed to load**). Open the chapter again. **Page N is not an image (the site may have shown a check page)** means the same thing: pass the site check first (see above), then retry.
- **This chapter is locked … (paid or early access)**: the site only gives the chapter to paying or signed-in readers. Lume can't open it. Read it from another linked source, or wait.
- **… offers this title only to signed-in readers, library members or subscribers**: sign in under Sources.

### Other messages

- **… is limiting requests. Try again in a minute.** Wait, or lower **Pages downloaded at once**.
- **… has changed its pages and can't be read for now**: the source needs an app update.
- **Not found on …**: the title or chapter was probably removed. Another source may have it.
- Notifications don't arrive: allow notifications for Lume and check the battery settings of your phone. Turn on **Auto-check library**.
- Downloads don't start: check **Download only on Wi-Fi**, the **Download folder** and the free space.

## FAQ

**Is Lume free? Does it have ads?** It is free and open source. No ads, no analytics.

**Does Lume host manga?** No. It reads catalogues and images from third-party sites, and your own files and servers.

**Can I use Mihon extensions?** No. See [Mihon and Tachiyomi extensions](#mihon-and-tachiyomi-extensions). You can import a Mihon backup.

**Where is my data?** On your phone. Sync and backups go only where you choose.

**Do I need an account?** No. Only trackers and some sites need a sign-in.

**Will an update delete my library?** No. Install over the old version. Backups from older versions keep loading.

**Which languages?** English and Russian.

**Can I read on a tablet or an e-ink screen?** Yes: two pages side by side, a navigation rail, and **E-ink mode**.

**Where do I report a source that broke?** On GitHub, with the error log attached.

## Settings reference

This section follows **Settings** from top to bottom. A search field (**Search settings**) at the top finds any setting by name or keyword and opens its page. Defaults are for a fresh install. Settings that appear only in some cases say so.

The Settings screen lists, in this order: **Appearance**, **Reader**, **Library and updates**, **Downloads**, **Sources and search**, **Trackers**, **Sync**, **Backup and restore**, **Privacy**, **Network and storage**, **About and licenses**.

### Appearance

| Setting | What it does | Default |
| --- | --- | --- |
| **Theme** | **System**, **Light** or **Dark**. | Dark |
| **Pure black** | A black background in the dark theme, for OLED screens. | Off |
| **Material You colours** | Colours from your wallpaper. Shown on Android 12 and newer only. | Off |
| **Language** (group) | **System default**, **English** or **Russian**. | System default |
| **Translate chapter labels** | Shows Russian labels such as "Том 1 Глава 5" from Russian sources in the app language. | On |

### Reader

Defaults for every title. The reader's own settings panel changes the same values. Guided view, cropping, splitting and side-by-side pages work in the paged modes only.

| Setting | What it does | Default |
| --- | --- | --- |
| **Fullscreen while reading** | Scrolling hides the controls and system bars; a tap brings them back. | On |
| **Keep screen on** | The screen doesn't dim or lock while a chapter is open. | On |
| **Seamless chapters** | At the end of a chapter the next one follows right away. | On |
| *Controls* | | |
| **Tap left / right edge to turn pages** | In page modes the left and right thirds turn pages; the middle shows the controls. | On |
| **Volume keys turn pages** | Volume down goes to the next page, volume up to the previous one. | Off |
| **Double-tap zoom** | Double-tap a page to zoom in, and again to zoom out. | On |
| *Pages* | | |
| **Guided panel view** | A tap or page turn moves from panel to panel, in reading order. | Off |
| **Crop borders** | Cuts off plain white or black margins. Not used in guided panel view. | Off |
| **Split double pages** | In portrait, a page wider than tall is shown as two halves. | Off |
| **Two pages side by side** | **Off**, **Landscape** or **Always**. | Landscape |
| **Load N pages ahead** | How many pages are loaded ahead: 0, 2, 4, 6, 8 or 10. More means less waiting and more data and memory. | 4 |
| **Chapters loaded ahead** | **Off**, **1** or **2**. While you read, the next chapters download into the cache in the background, one page at a time with pauses so sites don't block it. Downloaded chapters are skipped. Uses more data. | Off |

#### Reader settings panel

Inside the reader, the tune icon opens a panel with the options above and these extra ones. Some options appear only in the mode they apply to.

| Setting | What it does | Default |
| --- | --- | --- |
| **Reading mode** | **RTL**, **Webtoon** or **LTR**. | Webtoon |
| **Reader theme** | **Light**, **Dark** or **System**: the background around pages. | Dark |
| **Image gap N dp** (Webtoon) | Space between images, 0 to 32. | 0 |
| **Webtoon side margins N %** (Webtoon) | Empty margin on each side, 0 to 30 % (for tablets and landscape). | 0 |
| **Dim pages N %** | Extra darkening for night reading, 0 to 80 %. | 0 |
| **Own brightness while reading** | Turns on a **Brightness** slider for the reader only. | Off (system brightness) |
| **Colour filter** | **None**, **Sepia**, **Grey** or **Warm**. | None |
| **E-ink mode** | Grey, high-contrast pages and page turns without animation. | Off |
| **Screen orientation** | **Auto**, **Portrait** or **Landscape**. | Auto |
| **Auto-scroll speed** (Webtoon) | Speed of auto-scroll, 1 to 10. | 3 |
| **Tap zones in webtoon mode** (Webtoon) | Top third scrolls back, bottom third scrolls on. | Off |
| **Hide page number** | Hides the page number in the reader. | Off |
| **Tap left / right edge to turn pages**, **Guided panel view**, **Crop borders**, **Split double pages**, **Two pages side by side** (paged modes) | As above. | As above |
| **First page alone** (when side by side isn't Off) | The chapter's first page stands alone, so pairs match a printed book. | On |
| **Save for this title** | Keeps the current settings for this title only. | n/a |

The Settings screen sets the defaults. A change in the panel becomes the new default for every title, unless you tap **Save for this title**.

### Library and updates

**Updates**

| Setting | What it does | Default |
| --- | --- | --- |
| **Auto-check library** | Looks for new chapters in the background and sends a notification. | Off |
| **Check every** (when the check is on) | 3, 6, 12, 24 or 48 h. | 6 h |
| **Only on Wi-Fi** (when the check is on) | Checks wait for Wi-Fi. | Off |
| **Only while charging** (when the check is on) | Background checks wait for the charger; **Update now** runs at once. | Off |
| **Notify for categories** | Choose the categories whose new chapters raise a notification. | All categories |
| **Update categories** | Check only titles in the chosen categories. Also applies to **Update now**. | All categories |
| **Skip completed titles** | Leave out titles marked completed. | Off |
| **Skip titles you haven't started** | Leave out titles with no read chapter. | Off |
| **Skip titles with unread chapters** | Check a title again once you have caught up on it. | Off |

**Library**

| Setting | What it does | Default |
| --- | --- | --- |
| **Find titles on other sources** | When you add a title, Lume looks for it on your other sources and suggests linking them. | On |
| **Reading statistics** | Opens the screen with time, pages and chapters you have read. | n/a |
| **Categories** | Opens category management: add, rename, reorder, choose the **Default category** for new titles. | None |

**Local files**

| Setting | What it does | Default |
| --- | --- | --- |
| **Import a folder from the device** | Copies comics from a folder into Lume. | n/a |
| **Open local titles** | Opens the Local files source. | n/a |
| **Watched folders** / **Add a watched folder** | A library folder, or one series: Lume looks for new and changed files in it. Each folder shows its last scan, **Reconnect** and **Remove**. | None |
| **Scan now** | Scans the watched folders now. | n/a |
| **Scan every** | **Off**, 6, 12 or 24 hours. | Off |
| **Clean temporary files** | Removes unpacked archives left from reading local files. | n/a |

### Downloads

| Setting | What it does | Default |
| --- | --- | --- |
| **Download only on Wi-Fi** | Downloads wait for Wi-Fi. | Off |
| **Download new chapters** | **Off**, **All titles** or **Subscribed** (titles with the bell). Library checks queue the chapters they find. | Off |
| **Delete after reading** | Chapters you finish lose their download when you close the reader. | Off |
| **Save as CBZ** | One archive per chapter, with `ComicInfo.xml`. | Off |
| **Pages downloaded at once** | 1 to 4. More is faster, but some sites slow down or block quick downloads. | 2 |
| **Download folder** (Storage) | **Internal storage**, **SD card** / **External storage**, or **Choose a folder…**. | Internal storage |
| **Downloads** | Opens the queue, with pausing and storage used. | n/a |
| **Restore downloads from disk** | Finds downloaded chapters on the device again, with a confirmation. | n/a |

### Sources and search

| Setting | What it does | Default |
| --- | --- | --- |
| **Sources** | Opens the Sources screen: turn sources on and off, pin them, change their settings, add packs and servers. | n/a |
| **Include 18+ sources in global search** | Global search also asks the sources marked 18+. | On |
| **Similar titles** | The title screen shows what AniList (or MyAnimeList, if signed in) recommends. Lume sends them the title's name. | On |

### Trackers

| Setting | What it does | Default |
| --- | --- | --- |
| **Accounts**: AniList, Shikimori, MyAnimeList, Kitsu, MangaUpdates | **Log in** and **Log out** for each. Signed-in AniList and MyAnimeList also have **Import reading list**. | Not signed in |
| **Link trackers automatically** | When you add a title, signed-in trackers link it if they find the exact same name. | On |

### Sync

| Setting | What it does | Default |
| --- | --- | --- |
| **Sync through** | **Off**, **Folder** or **WebDAV**. Folder asks you to **Pick a folder…**. WebDAV (**WebDAV server**) asks for **Folder address**, **User name** and **Password or app password**. | Off |
| **Passphrase** | Encrypts the sync files (AES-256). Every device needs the same one. Empty turns it off. | Off |
| **Sync in the background** | **Manual**, 1, 6, 12 or 24 h. Lume also syncs when it starts. | 6 h |
| **Sync now** | Syncs now; shows the last sync, the other devices read and any error. | n/a |

### Backup and restore

| Setting | What it does | Default |
| --- | --- | --- |
| **Create backup** | Saves a backup inside the app. | n/a |
| **Save a backup to…** | Saves a backup wherever you choose. | n/a |
| **Restore** | Restores the newest backup saved inside the app. | n/a |
| **Restore from a file…** | A Lume, Mihon / Tachiyomi (`.tachibk`) or Kotatsu (`.bk.zip`) backup. | n/a |
| **Open a reading list…** (Reading lists) | Opens a reading list someone shared. | n/a |
| **Automatic backups** | **Off**, 1, 3 or 7 days. | Off |
| **Keep the newest** (when automatic backups are on) | 3, 5 or 10 backups. | 5 |
| **Also copy to a folder…** / **Stop copying to the folder** (when automatic backups are on) | Copies each automatic backup to a folder you pick. | No folder |

### Privacy

| Setting | What it does | Default |
| --- | --- | --- |
| **Incognito mode** | Reading leaves no history, progress or read marks. | Off |
| **App lock** | Asks for the screen lock when Lume opens. | Off |
| **Hide in recent apps** | Blank preview in the app switcher; screenshots are blocked. | Off |

### Network and storage

| Setting | What it does | Default |
| --- | --- | --- |
| **DNS over HTTPS** | **Off**, **Cloudflare**, **Google**, **AdGuard** or **Quad9**. Helps when your provider blocks sites by DNS. | Off |
| **Clear cache** | Removes saved covers and pages; they load again when needed. Downloads stay. | n/a |
| **Clear cookies** | Signs you out of sites and resets their checks (Cloudflare). Trackers stay signed in. | n/a |

### About and licenses

| Setting | What it does | Default |
| --- | --- | --- |
| **Check for updates** | Asks GitHub for a newer version; you can then download and install it, open the release page, skip or postpone it. | n/a |
| **Check for updates automatically** | Lume checks for a new version by itself. | On |
| **Source code**, **Releases and changes**, **Lume license (Apache 2.0)** | Open the pages in your browser. | n/a |
| **Error log** | Recent source errors, kept only on your phone. | n/a |
| **Share log** / **Clear crash log** | See [Crash log](#crash-log-and-reporting-bugs). | n/a |
| **Open-source libraries** | The libraries Lume ships with and their licenses. | n/a |

### Library options sheet

In the Library, **Sort, filter and display** opens three tabs.

| Setting | What it does | Default |
| --- | --- | --- |
| **Filter**: **Unread**, **Downloaded**, **Started**, **Tracking**, **Completed** | Tap once for **Only these**, again for **Hidden**, again for **Off**. | All Off |
| **Sort**: **Title**, **Last read**, **Date added**, **Unread chapters**, **Latest chapter** | Tap the same sort again to reverse it. | Title, A to Z |
| **Layout**: **Grid**, **Compact**, **List** | How titles are shown. | Grid |
| **Show chapters read of total** | A badge on each title. | On |
| **Show downloaded count** | A badge on each title. | On |
| **Show the number of sources** | A badge on each title. | On |
| **Filter chips above the titles** | Quick filter buttons over the grid. | On |
| **Continue button on started titles** | A **Continue reading** button on titles you started. | Off |

### Chapter list options

On the chapters screen: **Oldest first** or **Newest first** (default **Newest first**); filters **Unread**, **Read** and **With page bookmarks** (none on by default); and, for titles with several translation teams or sources, **Any team** or **One per chapter**, or **Show all copies** (off).

### Source and server settings

- **Source settings**: see [Source settings](#source-settings).
- **Add server** fields: see [Self-hosted servers](#self-hosted-servers-opds-komga-and-kavita).
