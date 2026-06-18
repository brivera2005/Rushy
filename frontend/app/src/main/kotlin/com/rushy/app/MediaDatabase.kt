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
        Index("epg_channel_id"),
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
    @ColumnInfo(name = "epg_channel_id") val epgChannelId: String? = null,
    val plot: String? = null,
    val rating: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
    @ColumnInfo(name = "tv_archive") val tvArchive: Boolean = false,
    @ColumnInfo(name = "tv_archive_duration") val tvArchiveDurationHours: Int? = null,
)

@Entity(
    tableName = "epg_programs",
    indices = [
        Index("channel_id"),
        Index(value = ["channel_id", "start_epoch_sec"]),
        Index("start_epoch_sec"),
        Index("end_epoch_sec"),
    ],
)
data class EpgProgramEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "channel_id") val channelId: String,
    val title: String,
    val description: String? = null,
    @ColumnInfo(name = "start_epoch_sec") val startEpochSec: Long,
    @ColumnInfo(name = "end_epoch_sec") val endEpochSec: Long,
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
        SELECT * FROM media_items WHERE source = :source AND is_hidden = 0
        AND (:categoryId = 'all' OR category_id = :categoryId)
        AND (:search = '' OR title LIKE '%' || :search || '%' COLLATE NOCASE)
        ORDER BY
            CASE WHEN rating IS NULL OR rating = '' THEN 0 ELSE CAST(rating AS REAL) END DESC,
            title COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getItemsPagedByRating(
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
        SELECT * FROM media_items
        WHERE is_favorite = 1 AND is_hidden = 0 AND source = :source
        ORDER BY title COLLATE NOCASE
        LIMIT :limit
        """,
    )
    suspend fun getFavoritesBySource(source: String, limit: Int): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE id IN (:ids) AND is_hidden = 0")
    suspend fun getByIds(ids: List<String>): List<MediaItemEntity>

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

    @Query("SELECT COUNT(*) FROM media_items WHERE is_favorite = 1 AND is_hidden = 0")
    suspend fun countFavorites(): Int

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MediaItemEntity?

    @Query(
        """
        SELECT DISTINCT epg_channel_id FROM media_items
        WHERE source = :source AND epg_channel_id IS NOT NULL AND epg_channel_id != '' AND is_hidden = 0
        """,
    )
    suspend fun getEpgChannelIds(source: String): List<String>

    @Query(
        """
        SELECT DISTINCT playbackId FROM media_items
        WHERE source = :source AND is_hidden = 0
        AND (epg_channel_id IS NULL OR epg_channel_id = '')
        AND playbackId IS NOT NULL AND playbackId != ''
        """,
    )
    suspend fun getLivePlaybackIdsWithoutEpg(source: String): List<String>
}

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM epg_programs")
    suspend fun countPrograms(): Int

    @Query(
        """
        SELECT * FROM epg_programs
        WHERE channel_id = :channelId
        AND end_epoch_sec > :windowStart AND start_epoch_sec < :windowEnd
        ORDER BY start_epoch_sec
        """,
    )
    suspend fun getProgramsForChannel(
        channelId: String,
        windowStart: Long,
        windowEnd: Long,
    ): List<EpgProgramEntity>

    @Query(
        """
        SELECT * FROM epg_programs
        WHERE channel_id IN (:channelIds)
        AND end_epoch_sec > :windowStart AND start_epoch_sec < :windowEnd
        ORDER BY channel_id, start_epoch_sec
        """,
    )
    suspend fun getProgramsForChannels(
        channelIds: List<String>,
        windowStart: Long,
        windowEnd: Long,
    ): List<EpgProgramEntity>
}

@Database(
    entities = [MediaItemEntity::class, EpgProgramEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class MediaDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun epgDao(): EpgDao

    companion object {
        @Volatile
        private var instance: MediaDatabase? = null

        fun getInstance(context: Context): MediaDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MediaDatabase::class.java,
                    "rushy_media.db",
                ).fallbackToDestructiveMigration()
                    .build().also { instance = it }
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
    epgChannelId = epgChannelId,
    plot = plot,
    rating = rating,
    isFavorite = isFavorite,
    isHidden = isHidden,
    tvArchive = tvArchive,
    tvArchiveDurationHours = tvArchiveDurationHours,
)

fun MediaItem.toEntity(): MediaItemEntity = MediaItemEntity(
    id = id,
    title = title,
    source = source.name,
    playbackId = playbackId,
    logoUrl = logoUrl,
    categoryId = categoryId,
    categoryName = categoryName,
    epgChannelId = epgChannelId,
    plot = plot,
    rating = rating,
    isFavorite = isFavorite,
    isHidden = isHidden,
    tvArchive = tvArchive,
    tvArchiveDurationHours = tvArchiveDurationHours,
)
