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

Sample manifests for one-site engines that ship only inside the app. They can't be imported; they are kept as a reference in case these sites are bundled again under `feature/comics/src/main/assets/sources/`.

- [site/weebcentral.json](site/weebcentral.json)
- [site/demonicscans.json](site/demonicscans.json)

## Source addresses: `hosts.json`

[hosts.json](hosts.json) lets a source move to a new address without an app update. Apps read it from `https://raw.githubusercontent.com/TheMiracleOfKein/lume/main/docs/packs/hosts.json` (and a jsDelivr copy when GitHub's raw host does not open), at most every 6 hours, and again soon after a host did not answer. Settings → Network → Update source addresses turns it off. Without the file, the addresses inside the app are used.

- `sources`: keyed by source id (`mangalib`) or by engine (`libsocial`, `ranobelib`, for every source of that engine); the source id wins. Each entry may set `apiBase`, `baseUrl` and `imageHosts` (a list) and nothing else. Headers, sign-in and engines never come from this file.
- `fallbacks`: a host that does not answer (no DNS, no connection, timeout of a GET), and the hosts to try instead, in order. The first that answers is used for the rest of the session.
- Every address must be `https`. A Lib source (`libsocial`, `ranobelib`) and any host a Lib request is sent to may only use the domains in `HostOverrides.SIGN_IN_DOMAINS`, because the sign-in goes to the API host; a Lib move to a brand-new domain needs an app update first.
- `schemaVersion` is 1. An app ignores a file with a higher number.
- Edit the file on `main`; the change reaches installed apps within hours. Keep the first `fallbacks` entry of each host the one most likely to work.
