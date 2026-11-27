# Browsing and sources

## Browsing and searching sources

Open **Catalog** to browse the active source. The **Source** entry in the menu switches to another one.

- The tabs are **Popular**, **Updates** and **Library**. **Library** lists only this source's titles that you already saved.
- Some sources also show sections such as **New titles**, **Top rated**, **Completed** or **Most viewed**.
- Open **Filters** to filter by things like **Genres**, **Tags**, **Status**, **Type**, **Content rating** or year, and to change the sort. Tap a genre or tag once to include it and twice to exclude it. Tap **Apply** to use the filters or **Reset** to clear them.
- The search field at the top searches this source only, together with the filters (the tune button). Results replace the tabs; **X** or **Back** returns to them.
- Some sites can't filter and search at the same time: Lume tells you to clear the search field.
- Titles whose name matches what you typed come first; titles that only mention it in the description come after.

**Global search** asks many sources at once:

- Open **Menu → Search**, or switch the search between **Current source** and **All sources**. **All sources** stays in the mode you're in: novel sources in Ranobe mode, comics sources in Manga mode.
- Results are grouped per source with a count. A source that is slow, down or blocked shows a short reason (for example **No answer in 15 s**) and doesn't stop the others.
- Rows keep their order while results arrive. Once every source has answered, the sources whose titles match your search best by name move to the top.
- Your last searches appear under **Recent searches**. Remove one, or tap **Clear**.
- 18+ sources are included by default. Turn off **Include 18+ sources in global search** to leave them out.
- Disabled sources are left out of global search too.

You can also open a title link from your browser, or share it to Lume.

## Sources and packs

Lume's sources are built into the app; there is nothing to install. Each mode has its own:

- **Manga mode**: MangaDex, MangaKatana, Asura Scans, MangaRead, HiveToons, Vortex Scans (English); MangaLib, SlashLib, YaoiLib, HentaiLib, ReManga, MangaBuff, InkStory (Russian); **Local files**.
- **Ranobe mode**: Royal Road, Scribble Hub, NovelFull, NOVGO (English); RanobeLib (Russian); **Local files** (EPUB and TXT books).
- In both: packs you installed and servers you added.

The table with languages and engines is in the [README](../../README.md#built-in-sources).

Manage them in **Settings → Sources and search → Sources** (or **Menu → Source**).

When a site moves to a new address, Lume usually learns it by itself from a small list in the project, without an app update (**Settings → Network and storage → Update source addresses**, on by default). Only addresses are fetched.

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

How packs are written is in [packs/README.md](../packs/README.md) and [ENGINES.md](../ENGINES.md).

### Mihon and Tachiyomi extensions

Lume does not load Mihon or Tachiyomi extension APKs, so you can't install them. The reasons are in [MIHON_EXTENSIONS.md](../MIHON_EXTENSIONS.md). What you can do:

- Import a Mihon or Tachiyomi backup (`.tachibk`). Titles from sources Lume has are added, with categories, tracker links and read chapters. See [Backups](sync-and-backups.md#backups).
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
- **Import books…** (Ranobe mode) copies picked `.epub` and `.txt` files as novels; see [Reading novels](novels.md).
- **Open local titles** takes you to them.
- **Watched folders** are re-scanned for new files and changed files, and new chapters are copied in with a notification. Add one with **Add a watched folder**, scan at once with **Scan now**, and choose how often with **Scan every**. If Android takes back access to a folder, tap **Reconnect**.
- **Clean temporary files** frees space used by unpacked archives.

Lume can't open RAR5 archives or password-protected PDFs. Repack them as CBZ.

