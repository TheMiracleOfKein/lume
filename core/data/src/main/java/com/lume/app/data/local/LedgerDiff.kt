package com.lume.app.data.local

/** What the ledger remembers about one copied document. */
data class LedgerEntry(val size: Long, val lastModified: Long, val destPath: String)

/** Decides which documents of a picked folder have to be copied again. No Android types, so it is unit-tested. */
object LedgerDiff {
    /**
     * - no ledger row and no copy on disk: new, copy it;
     * - no ledger row but a copy on disk (imported before the ledger existed): keep it, just record it;
     * - a row whose size and modified time still match and whose copy is still there: unchanged;
     * - anything else (changed on the device, or the copy was deleted): copy it again.
     */
    fun needsCopy(entry: LedgerEntry?, size: Long, lastModified: Long, destExists: Boolean): Boolean {
        if (entry == null) return !destExists
        return !destExists || entry.size != size || entry.lastModified != lastModified
    }
}
