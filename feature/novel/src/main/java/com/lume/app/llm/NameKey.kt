package com.lume.app.llm

import java.text.Normalizer
import java.util.Locale

/**
 * Forms of one name: "Yu San A", "Yu San A-san" and "Yu San A's" share the key "yu san a", so they
 * are one glossary term and one codex entry.
 *
 * Only what is safe to drop without knowing the language is dropped:
 * - a trailing possessive (`'s`, `’s`, `s'`);
 * - a trailing Japanese or Korean honorific written with a hyphen ("-san", "-nim", "-сама"), or
 *   attached in kana or hangul ("さん", "님"). With a space it stays: "Lin Yang" or "Zhao Kun" are
 *   Chinese names, not honorifics.
 * Chinese suffixes such as 兄 or 姐 stay too, since they can be part of a name; there the model's
 * base form decides.
 */
fun nameKey(name: String): String {
    var s = Normalizer.normalize(name, Normalizer.Form.NFKC).trim().replace('’', '\'').lowercase(Locale.ROOT)
    s = stripPossessive(s)
    s = HYPHEN_HONORIFIC.replace(s, "")
    s = stripNativeHonorific(s)
    return SEPARATORS.replace(s, " ").trim()
}

/**
 * [name] without its possessive and honorific, as written ("Yu San A-san" → "Yu San A"); [name]
 * itself when nothing is dropped or only the honorific would be left.
 */
fun baseName(name: String): String {
    val trimmed = name.trim()
    var s = stripPossessive(trimmed.replace('’', '\''))
    s = HYPHEN_HONORIFIC.replace(s, "")
    s = stripNativeHonorific(s)
    return s.trim().ifEmpty { trimmed }
}

/** Whether [name] is its own base form (no possessive or honorific to drop). */
fun isBaseName(name: String): Boolean = baseName(name) == name.trim()

private fun stripPossessive(s: String): String = when {
    s.length > 3 && (s.endsWith("'s") || s.endsWith("'S")) -> s.dropLast(2)
    s.length > 3 && (s.endsWith("s'") || s.endsWith("S'")) -> s.dropLast(1)
    else -> s
}

private fun stripNativeHonorific(s: String): String {
    for (suffix in NATIVE_HONORIFICS) {
        if (s.length > suffix.length && s.endsWith(suffix)) return s.dropLast(suffix.length)
    }
    return s
}

// Not "shi", "yang" or "gun": hyphenated pinyin ("Ou-yang") would lose a syllable.
private val HONORIFICS = listOf(
    // Japanese, romanized
    "san", "sama", "kun", "chan", "dono", "sensei", "senpai", "sempai",
    // Korean, romanized
    "nim", "ssi", "hyung", "hyeong", "noona", "nuna", "oppa", "unnie", "eonni", "sunbae", "seonbae",
    // Russian spellings of both
    "сан", "сама", "кун", "тян", "чан", "доно", "сэнсэй", "сенсей", "семпай", "сэмпай", "ним", "сси", "хён", "нуна", "оппа", "онни", "сонбэ",
)

// Only after a hyphen (any dash), at the end, case-insensitive.
private val HYPHEN_HONORIFIC = Regex(
    "[-‐‑–](?:" + HONORIFICS.joinToString("|") { Regex.escape(it) } + ")$",
    RegexOption.IGNORE_CASE,
)

// Longest first, so "ちゃん" isn't read as "ん".
private val NATIVE_HONORIFICS = listOf("ちゃん", "さん", "さま", "くん", "せんぱい", "先輩", "様", "殿", "님", "씨").sortedByDescending { it.length }

private val SEPARATORS = Regex("[\\s\\-‐‑–_·・]+")
