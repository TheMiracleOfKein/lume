# Reading novels (Ranobe mode)

Lume reads light novels, web novels and books in the same app as manga, with the same library, history, downloads, sync, backups and trackers. The **full** and **ranobe** builds have it; the manga build doesn't.

What you get in Ranobe mode:

- **Sources**: RanobeLib (Russian), Royal Road and Scribble Hub (English), and your own EPUB and TXT files.
- **A text reader** that scrolls or turns pages, with your choice of font, size, spacing, margins and theme.
- **Read aloud** with any text-to-speech engine on the phone, screen off included.
- **Translation** of chapters (English ↔ Russian): Google, DeepL, an AI service with your key, or offline on the phone. See [Translation and AI](translation-and-ai.md).
- **Name lookup and codex**: long-press a name to see who it is, without spoilers.
- **"Previously…"**: a recap of where you stopped when you come back after a break.
- **Trackers**: novel lists on AniList, MyAnimeList and Shikimori.

## Switching mode

Press and hold the logo in the bottom bar (or the side rail), or open the main menu and tap **Mode**, then choose **Ranobe**. Lume then shows novels instead of comics. Choose **Manga** to go back. Your library, history and progress for both modes are kept. The ranobe build starts in Ranobe mode and has no switcher.

## RanobeLib

RanobeLib is a source in Ranobe mode, like MangaLib in Manga mode: Catalog, search, the site's genres, tags and other filters, title details and chapters with their translation teams. Ranobe, light novels and web novels all open in the text reader, with the chapter's pictures.

- **Sign in** under Sources (the key icon on the RanobeLib row) to read chapters that need an account. **⋮ → Import bookmarks** then brings your RanobeLib bookmarks into the Ranobe library, folders as categories, as it does for MangaLib.
- Paid early-access chapters aren't listed until they open for everyone.
- When a chapter has several translation teams, the reader shows the team's name under the chapter name. Tap it to switch to another team's translation; the next chapters follow that team.
- RanobeLib links opened from the browser or shared to Lume open the title.

## Royal Road and Scribble Hub

Two English sources for original web fiction, in Ranobe mode: Catalog (popular and latest), search, title details and the chapter list, read in the text reader and downloadable like RanobeLib chapters.

- Both sites sometimes show a Cloudflare check. Lume opens it in its browser; once it passes, the source loads (see [Cloudflare or DDoS-Guard checks](troubleshooting.md#cloudflare-or-ddos-guard-checks)).
- Royal Road chapters you have to pay for (early access through the author's Patreon) aren't listed.
- Author's notes before or after a chapter aren't shown, only the story.
- Links to a title or a chapter, opened from the browser or shared to Lume, open the title.
- **Filters**: Royal Road has sort order, status, type (original or fan fiction), and genres, content warnings and tags to include or exclude. Scribble Hub has sort order, status, and genres and tags to include or exclude, matching any or all of them.

## Downloading chapters

Novel chapters download like manga chapters: from the chapter list (select chapters, or **Download next N / all unread**), with **Download new chapters** for titles with the bell, and with **Delete after reading**. A chapter is saved as its text with its pictures, in the download storage you chose; the Downloads screen marks it **Text**. The reader opens a downloaded chapter without the internet. If a picture can't be downloaded, the text is still saved and the picture loads from the site when you're online.

Downloaded novel chapters can be sent to a nearby phone like manga chapters, with their pictures. The other phone needs Lume 1.3.0 or later, full or ranobe build. An older version or the manga build receives only the manga chapters, and the ranobe build only the novel ones.

## Importing books

- **Settings → Library and updates → Import books…** opens the file picker. Pick one or more `.epub` or `.txt` files; each becomes a book. You see how many were imported, or **Nothing to import** if no file could be used. Picking the same file again doesn't copy it twice.
- **Import a folder from the device** and **watched folders** also copy `.epub` and `.txt` files found in the folder or a few levels below it. A text file lying beside comic pages or comic archives is treated as a note, not a book.
- **Open local titles** shows the books.

EPUB 2 and EPUB 3 are read, with the cover and the chapter names from the book. For a TXT file Lume guesses the text encoding (UTF-8, windows-1251 and others) and splits it into chapters at heading lines such as "Chapter 12", "Глава 12" or "Prologue". A file with no such headings is cut into parts of about the same length, named "Part 1", "Part 2" and so on.

## The reader

The text scrolls vertically.

- Tap the middle to show or hide the bars.
- Tap the top or bottom of the screen to scroll up or down by about a screen.
- The next chapter follows right after the end of the current one.
- The bottom bar has the chapter list, **Read aloud** (headphones) and the reader settings.
- The top bar has translate, the codex (book icon) and "Previously…" (⟲). Tap the title to open the title page.

The settings panel has **Text size**, **Line spacing**, **Side margins**, **Paragraph spacing**, **Font** (serif or sans-serif), **Justify text** (long words are hyphenated so lines don't get wide gaps) and **Reader theme** (**App**, **Light**, **Sepia**, **Dark**, **Black**). **Page by page** turns the text into pages: swipe sideways, or tap the right or left edge, to turn them; a paragraph goes on over the next page, and each chapter starts on a new one. Changes show at once.

- **Save for this title** keeps text size, theme and page mode for this novel only. Font, margins and spacing stay shared.
- **Reset to defaults** puts the reader settings back (and drops what was saved for the title). It asks first.
- The defaults for every novel are in **Settings → Reader** (in Ranobe mode).

## Name lookup and codex

Long-press a name or term in the text (scroll or page mode) to open the lookup sheet:

- where it **first appeared** and in **how many chapters** it is mentioned;
- its **latest mentions**; tap one to see the paragraphs around it (your reading spot stays put);
- its glossary translation, if it has one, and **Show original** in a translated chapter.

Names of several words ("Lin Feng") are picked up whole, and you can edit the search in the sheet.

- **No spoilers.** Lookup only searches chapters up to the paragraph you pressed, even if later chapters are downloaded.
- **Offline.** It searches an index of the chapters you have read or downloaded, kept on the phone (**Settings → Reader → Story index**; **Clear story index** empties it). Nothing is indexed in incognito.
- **Codex.** Save a name from the sheet with its kind (character, place, group, item, term), other spellings and a note. Each title has its own codex screen (book icon in the top bar): search, sort, add, edit and delete. The codex is part of backups.

With an AI service set up, story notes can fill the codex for you and write short summaries; see [AI story notes and the codex](translation-and-ai.md#previously-ai-story-notes-and-the-codex).

## "Previously…"

When you come back to a title after a break (**Settings → Reader → "Previously…" recap**: 6 hours, 1 day or 3 days, or off), a small **Back after a break. Recall where you stopped?** bar offers **Recap**. It shows the last paragraphs before your spot. The ⟲ button in the top bar shows it any time. With AI story notes on, it also has **The story so far**.

## Read aloud

The headphones button on the bottom bar reads the chapter aloud with Android's own text-to-speech, starting at the text on screen. When the chapter ends, it goes on with the next one.

- It keeps playing with the screen off and after you leave the reader. The notification and the lock screen have play/pause and back / forward one paragraph, and headphone buttons work too. Unplugging headphones pauses it, and so does a phone call.
- The sentence being read is highlighted, and the text scrolls (or turns pages) along with it. If you scroll away by hand, the text stays where you put it; **Back to the voice** returns to the sentence being read.
- The panel above the bottom bar has back / forward one paragraph, play/pause, the read aloud settings and stop.
- The settings have **Speed**, **Sleep timer** (**End of chapter**, or 15, 30, 45 or 60 minutes), **Voice** and, when the phone has more than one, **Engine**. **Default for the text's language** picks a Russian voice for Russian text and an English one for English text when the engine has them. Voices marked **Needs internet** stream from the engine's servers; the others work offline.
- The spot being read is saved like normal reading progress, so **Continue reading** opens the book at the sentence where it stopped. A chapter read to its end is marked read.
- It needs no Google services: any installed engine works (Google, Samsung, RHVoice, eSpeak…). If the phone has none, Lume says so and opens Android's text-to-speech settings, where you can install one or download voices.
- Paused for 10 minutes, read aloud turns itself off.
- Read aloud settings stay on the phone (voices differ between phones); they aren't in backups or sync.

## Progress and history

Lume remembers the paragraph you stopped at, also after rotating the phone or when Android closes the app. **Continue reading** on the title and in History opens the book at that place. A book in History opens in the text reader straight after a start of the app.

## Trackers for novels

In Ranobe mode, **AniList**, **MyAnimeList** and **Shikimori** keep novel lists. Kitsu and MangaUpdates have manga only, so they appear only in Manga mode.

- The **Trackers** button on a novel searches the tracker's novels. Each result shows its format and year ("Light novel · 2018"), so a novel isn't linked to its manga by mistake.
- Chapters you finish in the text reader update the chapter count on every linked tracker.
- **Settings → Trackers → Import reading list** in Ranobe mode brings in your novel list.
- AniList has no entries for many Korean and Chinese web novels; Shikimori often does.

See [Trackers](trackers.md) for signing in.

## Not there yet

- Trackers made for novels (NovelUpdates, RanobeDB, novels on MangaUpdates).
- Sites with translated novels (such as NovelBin).
- More offline translation languages than English → Russian.

See the [roadmap](../ROADMAP.md).

