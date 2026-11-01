package com.lume.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCheckTest {
    @Test
    fun nothingCheckedIsNotAFailure() {
        val o = LibraryCheckOutcome(0, 0)
        assertFalse(o.allFailed)
        assertFalse(o.someFailed)
        assertFalse(o.shouldRetry(0))
    }

    @Test
    fun allFailedRetriesUntilTheLimit() {
        val o = LibraryCheckOutcome(3, 3)
        assertTrue(o.allFailed)
        assertTrue(o.shouldRetry(0))
        assertTrue(o.shouldRetry(1))
        assertFalse(o.shouldRetry(2))
    }

    @Test
    fun partialFailureIsSuccessWithAWarning() {
        val o = LibraryCheckOutcome(5, 2)
        assertFalse(o.allFailed)
        assertTrue(o.someFailed)
        assertFalse(o.shouldRetry(0))
    }
}
