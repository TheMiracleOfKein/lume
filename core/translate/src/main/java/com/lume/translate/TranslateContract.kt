package com.lume.translate

/** Names shared by Lume and the add-on. */
object TranslateContract {
    const val ADDON_PACKAGE = "com.lume.translate"
    const val SERVICE_ACTION = "com.lume.translate.BIND"
    const val PERMISSION = "com.lume.permission.TRANSLATE"

    /** Raised when [ILumeTranslator] changes incompatibly; Lume then asks for an add-on update. */
    const val API_VERSION = 1

    /** Characters per translate call, far below the 1 MB binder limit even as UTF-16 both ways. */
    const val MAX_BATCH_CHARS = 32_000

    /** Name of the add-on APK asset on each release: `lume-v<version>-translate.apk`. */
    const val RELEASE_FLAVOR = "translate"
}

/** One model file: where it lives on Mozilla's storage and what it must hash to once unpacked. */
data class ModelFile(
    /** Path under [ModelCatalog.BASE_URL], gzip-compressed. */
    val path: String,
    /** File name on the phone, unpacked. */
    val name: String,
    val compressedSize: Long,
    val size: Long,
    val sha256: String,
)

/** A language pair the add-on can download. [from] and [to] are ISO 639-1 codes. */
data class ModelPair(
    val from: String,
    val to: String,
    val model: ModelFile,
    val vocab: ModelFile,
    val shortlist: ModelFile,
) {
    val id: String get() = "$from-$to"
    val files: List<ModelFile> get() = listOf(model, vocab, shortlist)
    val downloadSize: Long get() = files.sumOf { it.compressedSize }
    val installedSize: Long get() = files.sumOf { it.size }
}

/**
 * Pinned Firefox Translations models (Mozilla, MPL-2.0 engine; models from Mozilla's public model
 * registry `db/models.json`). Paths, sizes and hashes are fixed here so the add-on never trusts a
 * list fetched at run time; a release updates them.
 */
object ModelCatalog {
    const val BASE_URL = "https://storage.googleapis.com/moz-fx-translations-data--303e-prod-translations-data/"

    private const val EN_RU = "models/en-ru/retrain_base-memory_KJ23-iDVTcymG1ZldWY17w/exported/"

    /** English → Russian, the "base-memory" model Mozilla marks for Android. */
    val EN_RU_PAIR = ModelPair(
        from = "en",
        to = "ru",
        model = ModelFile(
            path = EN_RU + "model.enru.intgemm.alphas.bin.gz",
            name = "model.bin",
            compressedSize = 21_988_864,
            size = 31_561_787,
            sha256 = "184cb5cda528eeefc0f75f5d0035d787b71d74af135e3c5608d01ae02ecfb920",
        ),
        vocab = ModelFile(
            path = EN_RU + "vocab.enru.spm.gz",
            name = "vocab.spm",
            compressedSize = 419_005,
            size = 904_455,
            sha256 = "56ee63e14e8cb926c394242adc3ed7cc602644c3d33058cff2ce2959d52a6258",
        ),
        shortlist = ModelFile(
            path = EN_RU + "lex.50.50.enru.s2t.bin.gz",
            name = "shortlist.bin",
            compressedSize = 1_378_563,
            size = 2_774_540,
            sha256 = "4d91839726b960e70b6d05c53d0cffd16262832b1c0e1ea99d66f412dcc6a239",
        ),
    )

    val pairs: List<ModelPair> = listOf(EN_RU_PAIR)

    fun find(id: String): ModelPair? = pairs.firstOrNull { it.id == id }

    /** The pair from [from] to [to], or null when no model covers it. */
    fun find(from: String, to: String): ModelPair? = find("$from-$to")
}
