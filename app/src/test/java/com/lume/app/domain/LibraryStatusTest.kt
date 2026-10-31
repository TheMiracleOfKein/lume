package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryStatusTest {
    @Test
    fun statusComesFromCategoryNames() {
        assertEquals(LibraryStatus.Dropped, libraryStatusOf(listOf("Брошено")))
        assertEquals(LibraryStatus.Planned, libraryStatusOf(listOf(" в планах ")))
        assertEquals(LibraryStatus.Completed, libraryStatusOf(listOf("Completed")))
        assertEquals(LibraryStatus.Reading, libraryStatusOf(listOf("Манхвы", "Читаю")))
    }

    @Test
    fun otherCategoriesOrNoneAreOther() {
        assertEquals(LibraryStatus.Other, libraryStatusOf(emptyList()))
        assertEquals(LibraryStatus.Other, libraryStatusOf(listOf("Любимые")))
    }
}
