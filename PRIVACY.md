# Privacy

Lume has no account, no ads, no analytics and no crash reporting. It has no server of its own.

## What stays on your phone

Your library, reading progress, page bookmarks, history, categories, downloads and settings are stored only on your phone. Backups are files that you choose where to save.

## Who Lume talks to

Lume connects only to these services, and only when you use the feature:

| When | Where | What is sent |
| --- | --- | --- |
| Browsing, searching, reading, updating the library | The source's website or API (for example mangadex.org, mangalib.me) | The requests needed to show titles, chapters and pages, like a browser would. Cookies from site checks or a site sign-in are sent to that site. |
| You sign in to a tracker | AniList, Shikimori, MyAnimeList, Kitsu or MangaUpdates | Sign-in, then the progress, status, score and dates of the titles you link. |
| You turn on DNS over HTTPS | The DNS provider you pick (Cloudflare, Google, AdGuard or Quad9) | The names of the sites Lume connects to. |
| You check packs for updates | raw.githubusercontent.com (or the catalogue address you set) | A request for the pack catalogue. |

Each of these services has its own privacy policy.

## Sign-ins and tokens

- Tracker tokens and site sign-ins are stored in the app's private storage.
- They are never included in Lume's backups.
- They are excluded from Android's cloud backup. Device-to-device transfer, which you start yourself, copies the whole app.

## Permissions

- **Internet, network state:** to load sources and check whether you are online.
- **Notifications:** library updates and download progress.
- **Foreground service (data sync):** downloads keep running while the app is in the background.

Lume doesn't ask for storage, contacts, location, camera or microphone access. Files are picked through the Android file picker.
