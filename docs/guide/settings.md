# Settings reference

This page follows **Settings** from top to bottom. A search field (**Search settings**) at the top finds any setting by name or keyword and opens its page. Defaults are for a fresh install. Settings that appear only in some cases say so.

The Settings screen lists, in this order: **Appearance**, **Reader**, **AI and translation** (Ranobe mode), **Library and updates**, **Downloads** (Manga mode), **Sources and search**, **Trackers**, **Sync**, **Backup and restore**, **Privacy**, **Network and storage**, **About and licenses**.

Settings follow the mode you're in. In Manga mode, **Reader** has the page reader's settings; in Ranobe mode, the text reader's.

## Appearance

| Setting | What it does | Default |
| --- | --- | --- |
| **Theme** | **System**, **Light** or **Dark**. | Dark |
| **Pure black** | A black background in the dark theme, for OLED screens. | Off |
| **Material You colours** | Colours from your wallpaper. Shown on Android 12 and newer only. | Off |
| **Language** (group) | **System default**, **English** or **Russian**. | System default |
| **Translate chapter labels** | Shows Russian labels such as "Том 1 Глава 5" from Russian sources in the app language. | On |

## Reader

Defaults for every title. The reader's own settings panel changes the same values. **Reset to defaults** at the bottom of the page puts them back, after asking.

### Reader in Manga mode

Guided view, cropping, splitting and side-by-side pages work in the paged modes only.

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
| **Panel order** | How panels in a row are read in guided view: right to left (manga) or **Left to right (comics)**. Separate from the page-turn direction. | Right to left |
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

### Reader in Ranobe mode

The text reader's defaults. The same options are in the reader's settings panel; see [Reading novels](novels.md#the-reader).

| Setting | What it does | Default |
| --- | --- | --- |
| **Text size**, **Line spacing**, **Side margins**, **Paragraph spacing** | How the text is laid out. | n/a |
| **Font** | Serif or sans-serif. | n/a |
| **Justify text** | Even line edges, with hyphenation of long words. | n/a |
| **Page by page** | Turn pages sideways instead of scrolling. | Off |
| **Reader theme** | **App**, **Light**, **Sepia**, **Dark** or **Black**. | App |
| *Story* | | |
| **Story index** | Keeps the text of chapters you read or download on the phone, so a long-pressed name can be looked up offline. Nothing is stored in incognito. | On |
| **"Previously…" recap** | After a break of 6 hours, 1 day or 3 days, the reader offers a recap of the last paragraphs before your spot. **Off** turns it off. | 1 day |
| **AI story notes** | **Off**, **After each chapter** or **When needed**. The active AI service writes a summary of each chapter, adds the names in it to the codex, and gives the recap **The story so far**. See [Translation and AI](translation-and-ai.md#previously-ai-story-notes-and-the-codex). | Off |
| **Clear story index** | Deletes the index and the chapter summaries. Shows how much space they use. | n/a |

## AI and translation

Shown in Ranobe mode. See [Setting up an AI service](translation-and-ai.md#setting-up-an-ai-service).

| Setting | What it does | Default |
| --- | --- | --- |
| **Active AI service** | **OpenRouter**, **OpenAI**, **DeepSeek**, **Gemini** or **Own server**. Translation, story notes, the recap and codex summaries all use it. Each service shows whether its key was checked and works. | OpenRouter |
| API key (per service) | Saved encrypted; only the last four characters are shown, with **Replace the key**, **Remove the key** and **Check now**. | None |
| **Model** | **Automatic**, or **Choose a model…** for the active service. | Automatic |
| **DeepL API key** | For the DeepL engine; its check shows the characters used this month. | None |
| **Used this month** | Requests, tokens and (when the service reports it) cost, per feature. | n/a |

## Library and updates

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
| **Import books…** | Copies picked EPUB and TXT files into Lume as novels. Ranobe mode only. | n/a |
| **Open local titles** | Opens the Local files source. | n/a |
| **Watched folders** / **Add a watched folder** | A library folder, or one series: Lume looks for new and changed files in it. Each folder shows its last scan, **Reconnect** and **Remove**. | None |
| **Scan now** | Scans the watched folders now. | n/a |
| **Scan every** | **Off**, 6, 12 or 24 hours. | Off |
| **Clean temporary files** | Removes unpacked archives left from reading local files. | n/a |

## Downloads

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

## Sources and search

| Setting | What it does | Default |
| --- | --- | --- |
| **Sources** | Opens the Sources screen: turn sources on and off, pin them, change their settings, add packs and servers. | n/a |
| **Include 18+ sources in global search** | Global search also asks the sources marked 18+. | On |
| **Similar titles** | The title screen shows what AniList (or MyAnimeList, if signed in) recommends. Lume sends them the title's name. | On |

## Trackers

| Setting | What it does | Default |
| --- | --- | --- |
| **Accounts**: AniList, Shikimori, MyAnimeList, Kitsu, MangaUpdates | **Log in** and **Log out** for each. Signed-in AniList, MyAnimeList and Shikimori also have **Import reading list**. | Not signed in |
| **Link trackers automatically** | When you add a title, signed-in trackers link it if they find the exact same name. | On |

## Sync

| Setting | What it does | Default |
| --- | --- | --- |
| **Sync through** | **Off**, **Folder** or **WebDAV**. Folder asks you to **Pick a folder…**. WebDAV (**WebDAV server**) asks for **Folder address**, **User name** and **Password or app password**. | Off |
| **Passphrase** | Encrypts the sync files (AES-256). Every device needs the same one. Empty turns it off. | Off |
| **Sync in the background** | **Manual**, 1, 6, 12 or 24 h. Lume also syncs when it starts. | 6 h |
| **Sync now** | Syncs now; shows the last sync, the other devices read and any error. | n/a |

## Backup and restore

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

## Privacy

| Setting | What it does | Default |
| --- | --- | --- |
| **Incognito mode** | Reading leaves no history, progress or read marks. | Off |
| **App lock** | Asks for the screen lock when Lume opens. | Off |
| **Hide in recent apps** | Blank preview in the app switcher; screenshots are blocked. | Off |

## Network and storage

| Setting | What it does | Default |
| --- | --- | --- |
| **Update source addresses** | When a site moves, Lume learns the new address from a small list in the project, without an app update. Only addresses are fetched: no code, nothing about you. Off: the addresses inside the app are used. | On |
| **DNS over HTTPS** | **Off**, **Cloudflare**, **Google**, **AdGuard** or **Quad9**. Helps when your provider blocks sites by DNS. | Off |
| **Clear cache** | Removes saved covers and pages; they load again when needed. Downloads stay. | n/a |
| **Clear cookies** | Signs you out of sites and resets their checks (Cloudflare). Trackers stay signed in. | n/a |

## About and licenses

| Setting | What it does | Default |
| --- | --- | --- |
| **Check for updates** | Asks GitHub for a newer version; you can then download and install it, open the release page, skip or postpone it. | n/a |
| **Check for updates automatically** | Lume checks for a new version by itself. | On |
| **Source code**, **Releases and changes**, **Lume license (Apache 2.0)** | Open the pages in your browser. | n/a |
| **Error log** | Recent source errors, kept only on your phone. | n/a |
| **Share log** / **Clear crash log** | See [Crash log](troubleshooting.md#crash-log-and-reporting-bugs). | n/a |
| **Open-source libraries** | The libraries Lume ships with and their licenses. | n/a |

## Library options sheet

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

## Chapter list options

On the chapters screen: **Oldest first** or **Newest first** (default **Newest first**); filters **Unread**, **Read** and **With page bookmarks** (none on by default); and, for titles with several translation teams or sources, **Any team** or **One per chapter**, or **Show all copies** (off).

## Source and server settings

- **Source settings**: see [Source settings](sources.md#source-settings).
- **Add server** fields: see [Self-hosted servers](sources.md#self-hosted-servers-opds-komga-and-kavita).
