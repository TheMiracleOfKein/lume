# Troubleshooting and FAQ

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

**Can Lume read novels?** Yes, in Ranobe mode (full and ranobe builds): RanobeLib, Royal Road, Scribble Hub, NovelFull, NOVGO, and your own EPUB and TXT files. See [Reading novels](novels.md).

**Which build should I install?** The full build, unless you only want manga or only novels. See [Pick a build](getting-started.md#pick-a-build).

**Do I have to pay for AI?** No. Translation with Google or on the phone is free, and AI features are off until you add your own key. With a key, you pay the AI service directly, usually cents per chapter.

**Does Lume host manga?** No. It reads catalogues and images from third-party sites, and your own files and servers.

**Can I use Mihon extensions?** No. See [Mihon and Tachiyomi extensions](sources.md#mihon-and-tachiyomi-extensions). You can import a Mihon backup.

**Where is my data?** On your phone. Sync and backups go only where you choose.

**Do I need an account?** No. Only trackers and some sites need a sign-in.

**Will an update delete my library?** No. Install over the old version. Backups from older versions keep loading.

**Which languages?** English and Russian.

**Can I read on a tablet or an e-ink screen?** Yes: two pages side by side, a navigation rail, and **E-ink mode**.

**Where do I report a source that broke?** On GitHub, with the error log attached.

