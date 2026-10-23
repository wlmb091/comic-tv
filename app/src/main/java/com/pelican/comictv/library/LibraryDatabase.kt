package com.pelican.comictv.library

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "comics")
data class ComicEntity(
    @PrimaryKey val id: String,
    val title: String,
    val pageCount: Int,
    val currentPage: Int = 0,
    val lastRead: Long = 0,
    val importedAt: Long = System.currentTimeMillis(),
    val fit: Int = 0,
    val rtl: Boolean = false,
)

@Dao
interface ComicDao {
    @Query("SELECT * FROM comics ORDER BY importedAt DESC") fun observe(): Flow<List<ComicEntity>>
    @Query("SELECT * FROM comics WHERE id = :id") suspend fun get(id: String): ComicEntity?
    @Query("SELECT id FROM comics") suspend fun ids(): List<String>
    @Insert suspend fun insert(comic: ComicEntity)
    @Query("UPDATE comics SET currentPage = min(max(:page, 0), max(pageCount - 1, 0)), lastRead = :time WHERE id = :id")
    suspend fun saveProgress(id: String, page: Int, time: Long)
    @Query("UPDATE comics SET fit = :fit, rtl = :rtl WHERE id = :id")
    suspend fun savePreferences(id: String, fit: Int, rtl: Boolean)
}

@Database(entities = [ComicEntity::class], version = 1, exportSchema = false)
abstract class LibraryDatabase : RoomDatabase() { abstract fun comics(): ComicDao }
