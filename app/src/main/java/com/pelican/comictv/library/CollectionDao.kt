package com.pelican.comictv.library

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "collections", indices = [Index(value = ["normalizedName"], unique = true)])
data class CollectionEntity(@PrimaryKey val id: String, val name: String, val normalizedName: String)

@Entity(tableName = "comic_collections", primaryKeys = ["comicId", "collectionId"],
    foreignKeys = [ForeignKey(entity = ComicEntity::class, parentColumns = ["id"], childColumns = ["comicId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("collectionId")])
data class CollectionMembership(val comicId: String, val collectionId: String)

@Dao
abstract class CollectionDao {
    @Query("SELECT * FROM collections ORDER BY normalizedName, id") abstract fun observe(): Flow<List<CollectionEntity>>
    @Query("SELECT * FROM comic_collections") abstract fun observeMemberships(): Flow<List<CollectionMembership>>
    @Query("SELECT collectionId FROM comic_collections WHERE comicId = :comicId") abstract suspend fun forComic(comicId: String): List<String>
    @Insert abstract suspend fun insert(collection: CollectionEntity)
    @Query("UPDATE collections SET name = :name, normalizedName = :normalizedName WHERE id = :id")
    abstract suspend fun rename(id: String, name: String, normalizedName: String): Int
    @Query("DELETE FROM collections WHERE id = :id") abstract suspend fun delete(id: String)
    @Query("DELETE FROM comic_collections WHERE comicId = :comicId") abstract suspend fun clearMemberships(comicId: String)
    @Insert abstract suspend fun insertMemberships(rows: List<CollectionMembership>)
    @Insert abstract suspend fun insertComic(row: ComicEntity)
    @Transaction open suspend fun insertRestoredComic(row: ComicEntity, ids: Set<String>) {
        insertComic(row)
        insertMemberships(ids.map { CollectionMembership(row.id, it) })
    }
    @Transaction open suspend fun replaceMemberships(comicId: String, collectionIds: Set<String>) {
        clearMemberships(comicId)
        insertMemberships(collectionIds.map { CollectionMembership(comicId, it) })
    }
}
