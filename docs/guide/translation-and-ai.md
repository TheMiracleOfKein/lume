# Translation and AI

These features are for novels (Ranobe mode). All of them are optional, and nothing is sent anywhere until you pick a service.

| Feature | What it needs | What leaves the phone |
| --- | --- | --- |
| Translation with **Google** | Nothing | Chapter text, to Google |
| Translation with **DeepL** | Your DeepL API key (free plan) | Chapter text, to DeepL |
| Translation with **AI** | An AI service with your key, or your own server | Chapter text, glossary and style note, to that service |
| Translation **on the phone** | The Lume Translate add-on and a language pack | Nothing |
| **AI story notes**, recap and codex summaries | An AI service with your key | Text of chapters you have read, up to your spot |

## Setting up an AI service

Open **Settings → AI and translation** (shown in Ranobe mode).

1. Under **Active AI service**, pick **OpenRouter**, **OpenAI**, **DeepSeek**, **Gemini** or **Own server** (any OpenAI-compatible address, such as Ollama or LM Studio on your computer).
2. Paste the service's API key. Lume checks it at once and shows the result under the service's name: **Key works · N models available**, **The service refused this key**, **no credits left**, and so on. **Check now** checks again.
3. **Model** is **Automatic** by default: a fast, inexpensive model of that service. **Choose a model…** picks another; **Back to automatic** returns.

The same page has the **DeepL API key** and **Used this month**: requests, tokens and (on OpenRouter) cost, for translation, story notes, the recap and codex summaries.

Keys are stored encrypted on the phone and are never part of backups or sync. The key check sends only the key to its own service, never chapter text.

## Translating chapters

English titles (Royal Road, Scribble Hub, NovelFull, NOVGO, English books you imported) can be read in Russian, and Russian ones (RanobeLib) in English. Tap the translate button in the reader's top bar and pick **How to translate**:

- **Google Translate**: online, free, nothing to set up. The chapter text goes to Google.
- **DeepL**: online, with your own key from a free DeepL API account (500,000 characters a month). The key is stored encrypted and never leaves the phone in a backup.
- **AI (your key)**: online, with a language model and your own API key. It handles names, tone and dialogue best and keeps names the same across chapters (see below). Pick a service: **OpenRouter** (one key for many models; **Automatic** takes a cheap, fast model such as DeepSeek V4 Flash or Gemini Flash Lite), **OpenAI**, **DeepSeek**, **Gemini**, or **Other (URL)** for any OpenAI-compatible server, such as Ollama or LM Studio on your computer (no key needed). **Choose…** lists the service's models, with prices on OpenRouter. It costs what the service charges, usually cents per chapter. Settings → AI and translation → **Used this month** shows how many requests and tokens translation, story notes, the recap and codex summaries used, and the cost when the service reports it (OpenRouter does). The key is stored encrypted and is never part of backups or sync. The chapter text goes to the service you picked.
- **On the phone**: offline and private, with the open-source models Firefox uses (English → Russian for now). It needs no Google services and sends no chapter text anywhere. The steps below set it up.

- With **On the phone**, the first time it offers **Install Lume Translate**: a small separate app (about 8 MB) from the same Lume release. Android asks you to confirm the install. Only Lume can use it. Its own icon opens a short page with the downloaded language packs and a button back to Lume.
- Then **Download language pack** fetches the English → Russian model (about 24 MB) from Mozilla's model storage. After that, translation works without internet. **Delete language pack** frees the space again.
- **Read in Russian** is saved for the title. The chapter appears in English first and turns into Russian from the paragraph you're on; the top bar shows how far it has got. Translated chapters are kept for a while, so going back is instant.
- Long-press a paragraph to see the original. The original can be selected and copied, and **Add to glossary** adds a name or term from it.
- **Glossary**: names and terms that must read the same in every chapter of this title, for example "Shirou → Широ" or "mana core → ядро маны". It works with every engine. The glossary fixes the spelling; word endings stay as the translation has them. It is part of backups and sync.
- **Open glossary** shows all of a title's terms on their own screen: search, sort by name or newest, tap a term to edit or delete it, **+** to add one, paste a whole list ("Shirou = Широ", one per line) and copy all terms to share them.
- With **AI (your key)**, the model also lists the names it meets for the first time (characters, places, organizations, items, techniques) with the translation it used. They go into the glossary as **Suggested** and are used straight away, so the next paragraphs and chapters write them the same way. In the glossary, **Accept**, edit or **Reject** each one, or **Accept all**. An accepted character, place, group or item is also added to the codex, with the translation as another spelling. A rejected name isn't suggested again.
- Forms of one name count as one: "Yu San A-san" and "Yu San A's" are suggested as "Yu San A", which already covers them in the text. Suggestions from older versions that repeat a name are cleared when the title or its glossary opens. If your own terms hold two forms of a name, the glossary shows them under **Same name, several forms** with **Merge**.
- **Style note** (AI only): a short note for this title, for example "keep -san and -sama" or "the friends talk informally". Changing it translates the chapters again.
- **Translate this chapter again** (AI only) asks the model again for the chapter you're reading, for example after accepting or editing names.
- Read aloud reads the translated text, with a Russian voice when the engine has one.
- Machine translation makes mistakes, mostly with genders and slang. The original is a long press away.

## "Previously…", AI story notes and the codex

The plain recap and name lookup work without AI; see [Reading novels](novels.md#name-lookup-and-codex). This section is about what an AI service adds.

**AI story notes** (Settings → Reader, off by default) use the AI service you set up ("AI (your key)") to keep notes on what you read, with or without translation. For each chapter, one request brings a short summary, the characters, places, groups and items in it with a fact about each, and how they relate. They are written in the title's translation language, or else the app's language.

- **After each chapter**: a chapter is sent once, when you finish it. Opening a title also fills in up to 10 earlier read chapters that have no notes yet, several per request. The recap and the codex are then ready without waiting.
- **When needed**: nothing is sent while you read. Opening the recap, or summarizing a name, sends the read chapters before your spot that have no notes yet, several per request.
- With **AI translation**, the notes come in the translation's own answers, so translated chapters cost no extra request.
- "Previously…" adds **The story so far**: the summaries of the last four chapters and the current chapter up to your spot, so it never tells what comes after. Opening it again at the same spot shows the same text without a new request.
- **The codex fills itself.** Names the notes find are added as entries marked **AI**, with their other forms ("Yu San A-san", the translation) as other spellings. Forms of one name meet in one entry, and a name already in your codex only gets its new spellings. Edit an AI entry and it becomes yours. Remove it and it isn't added again.
- **No spoilers.** An AI entry shows only from the chapter it first appeared in. Facts and AI relations show only from the chapter after the one they come from, since you may still be in it. In the lookup sheet (long press on a name), **Seen in earlier chapters** lists those facts, the latest last. This takes no request.
- **Summarize with AI** writes a short description from those facts, a small request. Asking again with no newer facts costs nothing. A name without facts (read before notes were on) is described from the summaries and the passages that mention it, as before. The codex screen shows each summary written up to where you are.
- Chapter text is sent to the AI service for this. Nothing is sent in incognito. Notes, facts and relations are part of backups. **Clear story index** also removes the chapter summaries.

