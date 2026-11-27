package com.lume.app.data.db

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

interface SourceQueries {
    @Query("SELECT * FROM source_packs ORDER BY installedAt ASC")
    suspend fun allSourcePacks(): List<SourcePackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourcePack(item: SourcePackEntity)

    @Query("DELETE FROM source_packs WHERE id = :id")
    suspend fun deleteSourcePack(id: String)

    // Title sources
    @Query("SELECT * FROM title_sources WHERE sourceId = :sourceId AND titleId = :titleId ORDER BY priority ASC, addedAt ASC")
    fun observeTitleSources(sourceId: String, titleId: String): Flow<List<TitleSourceEntity>>

    @Query("SELECT * FROM title_sources WHERE sourceId = :sourceId AND titleId = :titleId ORDER BY priority ASC, addedAt ASC")
    suspend fun titleSources(sourceId: String, titleId: String): List<TitleSourceEntity>

    @Query("SELECT * FROM title_sources")
    suspend fun allTitleSources(): List<TitleSourceEntity>

    /** Every linked source of every title (search results mark titles saved from another source). */
    @Query("SELECT * FROM title_sources WHERE state = 'linked'")
    fun observeLinkedTitleSources(): Flow<List<TitleSourceEntity>>

    /** Linked sources per title that has any (for the library's sources badge). */
    @Query("SELECT sourceId, titleId, COUNT(*) AS linked FROM title_sources WHERE state = 'linked' GROUP BY sourceId, titleId")
    fun observeLinkedSourceCounts(): Flow<List<LinkedSourceCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTitleSource(item: TitleSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTitleSources(items: List<TitleSourceEntity>)

    @Query("DELETE FROM title_sources WHERE sourceId = :sourceId AND titleId = :titleId AND linkSourceId = :linkSourceId AND linkTitleId = :linkTitleId")
    suspend fun deleteTitleSource(sourceId: String, titleId: String, linkSourceId: String, linkTitleId: String)

    @Query("DELETE FROM title_sources WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteTitleSources(sourceId: String, titleId: String)
}

interface WatchedFolderQueries {
    @Query("SELECT * FROM watched_folders ORDER BY addedAt")
    fun observeWatchedFolders(): Flow<List<WatchedFolderEntity>>

    @Query("SELECT * FROM watched_folders ORDER BY addedAt")
    suspend fun watchedFolders(): List<WatchedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchedFolder(folder: WatchedFolderEntity)

    @Query("DELETE FROM watched_folders WHERE treeUri = :treeUri")
    suspend fun deleteWatchedFolder(treeUri: String)

    @Query("UPDATE watched_folders SET available = :available WHERE treeUri = :treeUri")
    suspend fun setWatchedFolderAvailable(treeUri: String, available: Boolean)

    @Query("UPDATE watched_folders SET lastScanAt = :at WHERE treeUri = :treeUri")
    suspend fun setWatchedFolderScanned(treeUri: String, at: Long)

    @Query("SELECT * FROM imported_files WHERE treeUri = :treeUri")
    suspend fun importedFilesFor(treeUri: String): List<ImportedFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportedFile(file: ImportedFileEntity)

    @Query("DELETE FROM imported_files WHERE treeUri = :treeUri")
    suspend fun deleteImportedFilesFor(treeUri: String)
}
