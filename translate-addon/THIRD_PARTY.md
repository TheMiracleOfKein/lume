# Lume Translate: third-party code and data

The add-on's own code is Apache-2.0, like Lume. It builds these projects into `liblumetranslate.so`:

| Project | Where | License |
| --- | --- | --- |
| bergamot-translator | `third_party/bergamot-translator` (git submodule, pinned commit) | MPL-2.0 |
| marian-dev (Bergamot fork) | inside bergamot-translator | MIT |
| ruy, sentencepiece | inside marian-dev | Apache-2.0 |
| intgemm | inside marian-dev (x86_64 only) | MIT |
| ssplit-cpp | inside bergamot-translator | Apache-2.0 |
| PCRE2 10.39 | downloaded at build time, checksum pinned in `patches/ssplit-android.patch` | BSD-3-Clause |

The changes Lume makes to these sources are the files in `patches/`, applied when CMake configures the build. Their full sources, with the changes, are in this repository and its submodule.

Language packs are not part of the APK. The add-on downloads them on request from Mozilla's Firefox Translations model storage. Their paths and checksums are pinned in `core/translate` (`ModelCatalog`).
