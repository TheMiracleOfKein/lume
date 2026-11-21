package com.lume.translate.addon

import java.io.File

/**
 * The marian config for one downloaded pair, as Firefox runs its models: greedy decoding, 8-bit
 * matrix products with the model's own scales, and soft alignments so HTML tags (bold, italic,
 * glossary marks) are moved onto the translated words.
 */
internal object EngineConfig {
    fun yaml(dir: File, model: String, vocab: String, shortlist: String): String {
        fun path(name: String) = quote(File(dir, name).absolutePath)
        return """
            models:
              - ${path(model)}
            vocabs:
              - ${path(vocab)}
              - ${path(vocab)}
            shortlist:
              - ${path(shortlist)}
              - false
            beam-size: 1
            normalize: 1.0
            word-penalty: 0
            max-length-break: 128
            mini-batch-words: 1024
            workspace: 128
            max-length-factor: 2.0
            skip-cost: true
            cpu-threads: 0
            quiet: true
            quiet-translation: true
            gemm-precision: int8shiftAlphaAll
            alignment: soft
        """.trimIndent() + "\n"
    }

    /** A YAML double-quoted scalar. App paths have no quotes, but a stray one must not break the config. */
    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
