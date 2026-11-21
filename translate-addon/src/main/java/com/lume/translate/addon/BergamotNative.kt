package com.lume.translate.addon

/** The JNI functions of `liblumetranslate.so`. Not thread-safe: [Engine] calls them from one thread. */
internal object BergamotNative {
    init {
        System.loadLibrary("lumetranslate")
    }

    /** Loads a model from a marian YAML config (UTF-8); returns a handle for [translate] and [free]. */
    external fun load(config: ByteArray): Long

    external fun free(handle: Long)

    /** UTF-8 in, UTF-8 out, same order and count. */
    external fun translate(handle: Long, texts: Array<ByteArray>, html: Boolean): Array<ByteArray>
}
