package com.lume.app.domain

/**
 * File-name order people expect: case-insensitive, runs of digits compared as numbers
 * (`2.jpg` before `10.jpg`, `ch2/9` before `ch2/10`). Equal-looking names (`page01`, `page1`)
 * fall back to plain text order, so the result is always deterministic.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isAsciiDigit() && cb.isAsciiDigit()) {
                var ie = i
                while (ie < a.length && a[ie].isAsciiDigit()) ie++
                var je = j
                while (je < b.length && b[je].isAsciiDigit()) je++
                val da = a.substring(i, ie).trimStart('0')
                val db = b.substring(j, je).trimStart('0')
                // Longer number (after leading zeros) is bigger; same length compares digit by digit.
                if (da.length != db.length) return da.length.compareTo(db.length)
                val c = da.compareTo(db)
                if (c != 0) return c
                i = ie
                j = je
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        val rest = (a.length - i).compareTo(b.length - j)
        if (rest != 0) return rest
        return a.compareTo(b)
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'
}
