# Privacy

Lume has no account, no ads, no analytics and no crash reporting. It has no server of its own.

## What stays on your phone

Your library, reading progress, page bookmarks, history, categories, downloads and settings are stored only on your phone. Backups are files that you choose where to save.

## Who Lume talks to

Lume connects only to these services, and only when you use the feature:

| When | Where | What is sent |
| --- | --- | --- |
| Browsing, searching, reading, updating the library | The source's website or API (for example mangadex.org, mangalib.me) | The requests needed to show titles, chapters and pages, like a browser would. Cookies from site checks or a site sign-in are sent to that site. |
| You open a title, with **Similar titles** on (Settings → Sources and search) | graphql.anilist.co; api.myanimelist.net when you are signed in to MyAnimeList and AniList has no recommendations | The title's name (or its AniList or MyAnimeList ID when the title is linked), to find the title and its recommendations. No sign-in or account details go to AniList. Turn the setting off and no request is made. |
| You sign in to a tracker | AniList, Shikimori, MyAnimeList, Kitsu or MangaUpdates | Sign-in, then the progress, status, score and dates of the titles you link. |
| You tap **Import reading list** for AniList or MyAnimeList (Settings → Trackers) | graphql.anilist.co or api.myanimelist.net, then each of your enabled sources | Your tracker sign-in token, to read your own Reading list from the tracker (titles, progress, score). Then the names (and authors) of those titles, to each of your sources' search, like "Found for you". Nothing is sent until you confirm. |
| You turn on **Sync** with WebDAV (Settings → Sync) | The WebDAV server whose address you enter (your Nextcloud, NAS or other server) | Your user name and password, and this device's sync file: your library, categories, reading spots, read marks, page bookmarks, linked sources, tracker links (not tracker sign-ins), per-title reader settings and source packs. With a passphrase, the file is encrypted on the phone before it is sent. Sync through a folder sends nothing itself: the app that keeps the folder in step (for example Syncthing) does that. |
| You send or receive downloads (Downloads → **Send to a nearby device** / **Receive**) | Another phone on your local network, never the internet | While the send screen is open, the phone announces itself on the local network (its device name, under `_lume._tcp`) and accepts one connection at a time. After both screens show the same six-digit code and you confirm, it sends the chosen chapters' pages, the titles' details and chapter lists and, when you turn it on, read marks. Everything after pairing is encrypted with a key made for that connection only. The receiver saves nothing until you tap **Accept**. |
| You turn on DNS over HTTPS | The DNS provider you pick (Cloudflare, Google, AdGuard or Quad9) | The names of the sites Lume connects to. |
| Lume starts (**Check for updates automatically** is on by default; Settings → About) or you tap **Check for updates** | api.github.com (the releases of TheMiracleOfKein/lume) | One request for the latest release, at most once every 6 hours when automatic. It carries Lume's name and version, like a browser's user agent, and no account or library data. Turn the setting off and Lume checks only when you tap the button. |
| You tap **Download** on an update | github.com (the release assets of TheMiracleOfKein/lume; GitHub may pass the file on to its own download servers) | A request for the APK. Lume checks its size and checksum before it offers to install it. |
| You check packs for updates | raw.githubusercontent.com (or the catalogue address you set) | A request for the pack catalogue. |

Each of these services has its own privacy policy.

## Sign-ins and tokens

- Tracker tokens and site sign-ins are stored in the app's private storage.
- They are never included in Lume's backups.
- The sync settings (folder, WebDAV address, user name, password and passphrase) are stored the same way and are never part of a sync file or a backup.
- They are excluded from Android's cloud backup. Device-to-device transfer, which you start yourself, copies the whole app.

## Permissions

- **Internet, network state:** to load sources and check whether you are online.
- **Local network** (Android 17 and later): to reach a server on your home network, and for sending downloads to a nearby phone. Android asks the first time you use either.
- **Notifications:** library updates and download progress.
- **Install packages:** only to install a Lume update that you downloaded, when you tap install. Android then asks you to confirm. Lume installs nothing by itself and nothing else.
- **Foreground service (data sync):** downloads keep running while the app is in the background.

Lume doesn't ask for storage, contacts, location, camera or microphone access. Files are picked through the Android file picker.
