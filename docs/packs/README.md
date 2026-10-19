# Source packs

A pack is a small JSON file that adds a site to Lume when the site runs on a platform Lume already supports. No app update is needed. See [ENGINES.md](../ENGINES.md) for how engines and packs fit together.

## Importable packs (Madara, LibSocial, HiveToons)

A pack's `"engine"` must be `madara`, `libsocial` or `hivetoons`. To add one:

- **From the catalogue:** **Sources → Check packs and updates** lists the packs in [index.json](index.json) and installs them.
- **From a link:** host the JSON file anywhere over HTTPS and use **Sources → Import pack from URL**.

Examples:

- [madara/likemanga.json](madara/likemanga.json)
- [madara/toongod.json](madara/toongod.json)

Both sites often show Cloudflare checks; Lume opens them in its built-in browser.

## Catalogue

[index.json](index.json) lists the importable packs. Lume reads it in **Sources → Check packs and updates**, installs packs from it and offers an update when a pack's `version` there is newer than the installed one. Pack `url`s are relative to the index.

Installed apps read the catalogue from `https://raw.githubusercontent.com/TheMiracleOfKein/lume/main/docs/packs/index.json`, so this file must stay at this path. To add a pack:

1. Add its JSON file under `madara/`, `libsocial/` or `hivetoons/`.
2. Add an entry to `index.json` (`id`, `name`, `version`, `engine`, `language`, `nsfw`, `url`).
3. When you change a pack later, raise its `version` in both files so installed apps offer the update.

## Site manifests (not importable)

Sample manifests for one-site engines that ship only inside the app. They can't be imported; they are kept as a reference in case these sites are bundled again under `app/src/main/assets/sources/`.

- [site/weebcentral.json](site/weebcentral.json)
- [site/demonicscans.json](site/demonicscans.json)
