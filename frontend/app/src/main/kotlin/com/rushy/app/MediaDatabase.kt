package com.rushy.app

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(
    tableName = "media_items",
    indices = [
        Index("source"),
        Index("category_id"),
        Index(value = ["source", "category_id"]),
    ],
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val source: String,
    val playbackId: String,
    val logoUrl: String?,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "category_name") val categoryName: String?,
    val plot: String? = null,
    val rating: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
)

data class CategoryRow(
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "category_name") val categoryName: String?,
)

@Dao
interface MediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaItemEntity>)

    @Query("DELETE FROM media_items WHERE source IN (:sources)")
    suspend fun deleteBySources(sources: List<String>)

    @Query("SELECT COUNT(*) FROM media_items WHERE source = :source AND is_hidden = 0")
    suspend fun countBySource(source: String): Int

    @Query(
        """
        SELECT DISTINCT category_id, category_name FROM media_items
        WHERE source = :source AND category_id IS NOT NULL AND category_id != '' AND is_hidden = 0
        ORDER BY category_name COLLATE NOCASE
        """,
    )
    suspend fun getCategories(source: String): List<CategoryRow>

    @Query(
        """
        SELECT * FROM media_items WHERE source = :source AND is_hidden = 0
        AND (:categoryId = 'all' OR category_id = :categoryId)
        AND (:search = '' OR title LIKE '%' || :search || '%' COLLATE NOCASE)
        ORDER BY title COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getItemsPaged(
        source: String,
        categoryId: String,
        search: String,
        limit: Int,
        offset: Int,
    ): List<MediaItemEntity>

    @Query(
        """
        SELECT COUNT(*) FROM media_items WHERE source = :source AND is_hidden = 0
        AND (:categoryId = 'all' OR category_id = :categoryId)
        AND (:search = '' OR title LIKE '%' || :search || '%' COLLATE NOCASE)
        """,
    )
    suspend fun countInCategory(source: String, categoryId: String, search: String): Int

    @Query("SELECT * FROM media_items WHERE is_favorite = 1 AND is_hidden = 0 ORDER BY title LIMIT :limit")
    suspend fun getFavorites(limit: Int): List<MediaItemEntity>

    @Query(
        """
        SELECT * FROM media_items WHERE is_hidden = 0 AND title LIKE '%' || :query || '%' COLLATE NOCASE
        LIMIT :limit
        """,
    )
    suspend fun searchAll(query: String, limit: Int): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE source = :source AND is_hidden = 0 ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomFeatured(source: String, limit: Int): List<MediaItemEntity>

    @Query("UPDATE media_items SET is_favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("UPDATE media_items SET is_hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)

    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun totalCount(): Int

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MediaItemEntity?
}

@Database(entities = [MediaItemEntity::class], version = 1, exportSchema = false)
abstract class MediaDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile
        private var instance: MediaDatabase? = null

        fun getInstance(context: Context): MediaDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MediaDatabase::class.java,
                    "rushy_media.db",
                ).build().also { instance = it }
            }
        }
    }
}

fun MediaItemEntity.toMediaItem(): MediaItem = MediaItem(
    id = id,
    title = title,
    source = MediaSource.valueOf(source),
    playbackId = playbackId,
    logoUrl = logoUrl,
    categoryId = categoryId,
    categoryName = categoryName,
    plot = plot,
    rating = rating,
    isFavorite = isFavorite,
    isHidden = isHidden,
)

fun MediaItem.toEntity(): MediaItemEntity = MediaItemEntity(
    id = id,
    title = title,
    source = source.name,
    playbackId = playbackId,
    logoUrl = logoUrl,
    categoryId = categoryId,
    categoryName = categoryName,
    plot = plot,
    rating = rating,
    isFavorite = isFavorite,
    isHidden = isHidden,
)
