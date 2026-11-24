# Sync and backups

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

A backup holds the library, categories, progress, bookmarks and settings. Sign-ins are not included. The format is documented in [BACKUP_FORMAT.md](../BACKUP_FORMAT.md), and older backups keep loading.

Open **Settings → Backup and restore**:

- **Create backup** saves a backup inside the app.
- **Save a backup to…** saves the same backup wherever you choose (Downloads, a cloud folder).
- **Restore** restores the newest backup saved inside the app.
- **Restore from a file…** restores a file you pick: a Lume backup, a Mihon or Tachiyomi backup (`.tachibk`) or a Kotatsu backup (`.bk.zip`).
- **Automatic backups**: choose **Off**, 1, 3 or 7 days. Then choose **Keep the newest** 3, 5 or 10 backups. **Also copy to a folder…** copies each one to a folder you pick; **Stop copying to the folder** turns that off.

### Importing from other apps

- **Mihon or Tachiyomi** (`.tachibk`): Lume adds titles from sources it has, with categories, tracker links and read chapters, and shows a progress bar while it reads chapter lists. Titles from sources Lume doesn't have are skipped, and the result names them.
- **Kotatsu** (`.bk.zip`): titles are added. Reading history isn't imported.
- **Tracker lists**: see [Trackers](trackers.md).
- **MangaLib bookmarks**: in Sources, open the three-dot menu of MangaLib, YaoiLib or HentaiLib (signed in) and tap **Import bookmarks**. Lume loads your bookmarks, adds the titles, turns bookmark folders into categories and then reads progress. You can leave the screen while it runs.

