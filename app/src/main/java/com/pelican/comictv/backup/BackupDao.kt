package com.pelican.comictv.backup

import androidx.room.*
import com.pelican.comictv.library.*

@Dao
interface BackupDao {
    @Query("SELECT * FROM comics") suspend fun comics(): List<ComicEntity>
    @Query("SELECT * FROM collections") suspend fun collections(): List<CollectionEntity>
    @Query("SELECT * FROM comic_collections") suspend fun links(): List<CollectionMembership>
    @Upsert suspend fun putComics(rows: List<ComicEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putLinks(rows: List<CollectionMembership>)
}
