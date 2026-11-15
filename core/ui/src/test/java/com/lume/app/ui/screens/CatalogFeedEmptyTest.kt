package com.lume.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFeedEmptyTest {
    @Test
    fun loadedWithoutItemsIsEmpty() {
        assertTrue(catalogFeedIsEmpty(loadedOnce = true, loading = false, error = null, itemCount = 0))
    }

    @Test
    fun notLoadedYetLoadingOrFailedIsNotEmpty() {
        assertFalse(catalogFeedIsEmpty(loadedOnce = false, loading = false, error = null, itemCount = 0))
        assertFalse(catalogFeedIsEmpty(loadedOnce = true, loading = true, error = null, itemCount = 0))
        assertFalse(catalogFeedIsEmpty(loadedOnce = true, loading = false, error = "boom", itemCount = 0))
    }

    @Test
    fun feedWithItemsIsNotEmpty() {
        assertFalse(catalogFeedIsEmpty(loadedOnce = true, loading = false, error = null, itemCount = 3))
    }
}
