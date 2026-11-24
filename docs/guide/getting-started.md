# Getting started

## Pick a build

Every release has three APKs. They share one app ID and signature, so you can install one over another and keep your data.

| APK | What it reads | Mode switcher |
| --- | --- | --- |
| `lume-vX.Y.Z-full.apk` | Manga, manhwa, webtoons **and** novels | Yes |
| `lume-vX.Y.Z-manga.apk` | Manga, manhwa and webtoons only | No |
| `lume-vX.Y.Z-ranobe.apk` | Novels (ranobe, light and web novels) only | No |

Not sure? Take **full**. The in-app updater stays on the build you installed.

**Lume Translate** (`lume-translate-vX.Y.Z.apk`) is an optional add-on for translating novel chapters offline, on the phone. You don't need to download it yourself: Lume offers to install it when you pick that engine ([Translation and AI](translation-and-ai.md)).

## Install

1. Download the APK from the [latest release](https://github.com/TheMiracleOfKein/lume/releases/latest). Lume needs Android 8.0 or newer.
2. Open the file. If Android blocks the install, allow installs from your browser or file manager, then open the file again.
3. To update, install the new APK over the old one, or use **Settings → About and licenses → Check for updates**. Your library, progress and downloads stay.

The [README](../../README.md#download) shows how to check the signature of a download.

## Reading modes: Manga and Ranobe

The full build has two modes. **Manga** is for anything drawn (manga, manhwa, manhua, webtoons, comics). **Ranobe** is for text (light novels, web novels, EPUB and TXT books).

- To switch, press and hold the logo in the bottom bar (or the side rail), or open the menu and tap **Mode**.
- Each mode has its own library, history, sources, catalog, updates and statistics. Nothing is lost when you switch.
- Some settings belong to one mode: the page reader's settings and **Downloads** to Manga; the text reader's settings and **AI and translation** to Ranobe.
- The manga and ranobe builds have one mode each and no switcher.

## First launch

Lume opens on **Home**. There is no account and no sign-up. Nothing is sent anywhere unless you sign in to a tracker or turn on sync.

The bar at the bottom has five places:

- **Home**: where you stopped and what is new.
- **Library**: the titles you saved.
- **Catalog**: browse and search a source.
- **Updates**: new chapters from your library.
- **Menu**: theme, **Incognito**, **Search**, **Source**, **Settings**, **Downloads**, **History** and **About and licenses**.

A good first run:

1. Open **Catalog** and pick a source (see [Browsing and searching sources](sources.md#browsing-and-searching-sources)).
2. Open a title and tap the bookmark icon to add it to the library.
3. Tap **Read**.

Already use another app? Bring your library over from a Mihon, Tachiyomi or Kotatsu backup, a tracker list, or your MangaLib / RanobeLib bookmarks ([Importing from other apps](sync-and-backups.md#importing-from-other-apps)).

On a tablet the bottom bar becomes a navigation rail.

The first time you turn on **Auto-check library**, Android 13 or newer asks to allow notifications. Allow them if you want to hear about new chapters.

## Home screen

Home shows:

- **Continue reading**: the titles you read last. Tap one to open the exact spot where you stopped.
- **Currently reading** and **New releases**: feeds from your sources.
- When you have no connection, **Offline · downloaded titles** shows what you have downloaded.

Pull down to refresh. If a feed fails you see **Couldn't refresh the feed**.

You can remove one title from Continue reading with **Remove from history**. Its chapters keep their read marks and page positions. **Clear history** removes every Continue reading card and saved position, so Lume asks you first.

There is also a **Continue reading** widget for the home screen of your phone, and launcher shortcuts (long-press the Lume icon). If you turned on **Hide in recent apps**, the widget asks you to open Lume instead of showing titles.

