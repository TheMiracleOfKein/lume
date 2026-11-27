package com.lume.app.data.db

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

interface CategoryQueries {
    // Categories
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    suspend fun allCategories(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(item: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun renameCategory(id: Long, name: String)

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setCategoryOrder(id: Long, sortOrder: Int)
}

interface TitleMetaQueries {
    // Title meta
    @Query("SELECT * FROM title_meta")
    fun observeTitleMeta(): Flow<List<TitleMetaEntity>>

    @Query("SELECT * FROM title_meta WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    suspend fun getTitleMeta(sourceId: String, titleId: String): TitleMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTitleMeta(item: TitleMetaEntity)

    // Title prefs
    @Query("SELECT * FROM title_prefs WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    suspend fun getTitlePrefs(sourceId: String, titleId: String): TitlePrefsEntity?

    @Query("SELECT * FROM title_prefs")
    suspend fun allTitlePrefs(): List<TitlePrefsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTitlePrefs(item: TitlePrefsEntity)

    @Query("SELECT * FROM title_prefs WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    fun observeTitlePrefs(sourceId: String, titleId: String): Flow<TitlePrefsEntity?>

    @Query("DELETE FROM title_meta WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteTitleMeta(sourceId: String, titleId: String)

    @Query("DELETE FROM title_prefs WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteTitlePrefs(sourceId: String, titleId: String)
}

interface TrackerQueries {
    // Tracker links
    @Query("SELECT * FROM tracker_links WHERE sourceId = :sourceId AND titleId = :titleId")
    fun observeTrackerLinks(sourceId: String, titleId: String): Flow<List<TrackerLinkEntity>>

    @Query("SELECT * FROM tracker_links WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun getTrackerLinks(sourceId: String, titleId: String): List<TrackerLinkEntity>

    @Query("SELECT * FROM tracker_links")
    suspend fun allTrackerLinks(): List<TrackerLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackerLink(item: TrackerLinkEntity)

    @Query("DELETE FROM tracker_links WHERE sourceId = :sourceId AND titleId = :titleId AND tracker = :tracker")
    suspend fun deleteTrackerLink(sourceId: String, titleId: String, tracker: String)

    @Query("SELECT DISTINCT sourceId, titleId FROM tracker_links")
    fun observeTrackedTitles(): Flow<List<TitleKey>>

    @Query("DELETE FROM tracker_links WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteTrackerLinksFor(sourceId: String, titleId: String)
}

interface GlossaryQueries {
    // Translation glossary (rejected suggestions are left out: they only stop the same proposal)
    @Query("SELECT * FROM glossary_terms WHERE sourceId = :sourceId AND titleId = :titleId AND status != 'rejected' ORDER BY source COLLATE NOCASE")
    fun glossary(sourceId: String, titleId: String): Flow<List<GlossaryTermEntity>>

    @Query("SELECT * FROM glossary_terms WHERE sourceId = :sourceId AND titleId = :titleId AND status != 'rejected' ORDER BY source COLLATE NOCASE")
    suspend fun glossaryOnce(sourceId: String, titleId: String): List<GlossaryTermEntity>

    /** Every source spelling of the title's glossary, rejected ones included, for skipping known proposals. */
    @Query("SELECT source FROM glossary_terms WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun glossarySources(sourceId: String, titleId: String): List<String>

    @Query("UPDATE glossary_terms SET status = :status, updatedAt = :now WHERE sourceId = :sourceId AND titleId = :titleId AND source = :source")
    suspend fun setGlossaryStatus(sourceId: String, titleId: String, source: String, status: String, now: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGlossaryTermsIfNew(terms: List<GlossaryTermEntity>)

    @Query("SELECT * FROM glossary_terms")
    suspend fun allGlossaryTerms(): List<GlossaryTermEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGlossaryTerm(term: GlossaryTermEntity)

    @Query("DELETE FROM glossary_terms WHERE sourceId = :sourceId AND titleId = :titleId AND source = :source")
    suspend fun deleteGlossaryTerm(sourceId: String, titleId: String, source: String)
}
