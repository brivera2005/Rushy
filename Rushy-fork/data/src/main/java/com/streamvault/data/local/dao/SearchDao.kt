package com.streamvault.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class SearchHitEntity(
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "content_id") val contentId: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "section_rank") val sectionRank: Int,
    @ColumnInfo(name = "match_rank") val matchRank: Int
)

@Dao
abstract class SearchDao {
    @Query(
        """
        SELECT * FROM (
            SELECT 'LIVE' AS content_type,
                   c.id AS content_id,
                   c.name AS title,
                   0 AS section_rank,
                   CASE
                       WHEN LOWER(c.name) = LOWER(:rawQuery) THEN 0
                       WHEN LOWER(c.name) LIKE LOWER(:prefixLike) ESCAPE '\' THEN 1
                       ELSE 2
                   END AS match_rank
            FROM channels c
            JOIN channels_fts ON c.id = channels_fts.rowid
            WHERE c.provider_id = :providerId
              AND channels_fts MATCH :ftsQuery
            ORDER BY match_rank ASC, c.name ASC
            LIMIT :limitPerSection
        )
        UNION ALL
        SELECT * FROM (
            SELECT 'MOVIE' AS content_type,
                   m.id AS content_id,
                   m.name AS title,
                   1 AS section_rank,
                   CASE
                       WHEN LOWER(m.name) = LOWER(:rawQuery) THEN 0
                       WHEN LOWER(m.name) LIKE LOWER(:prefixLike) ESCAPE '\' THEN 1
                       ELSE 2
                   END AS match_rank
            FROM movies m
            JOIN movies_fts ON m.id = movies_fts.rowid
            WHERE m.provider_id = :providerId
              AND movies_fts MATCH :ftsQuery
            ORDER BY match_rank ASC, m.name ASC
            LIMIT :limitPerSection
        )
        UNION ALL
        SELECT * FROM (
            SELECT 'SERIES' AS content_type,
                   s.id AS content_id,
                   s.name AS title,
                   2 AS section_rank,
                   CASE
                       WHEN LOWER(s.name) = LOWER(:rawQuery) THEN 0
                       WHEN LOWER(s.name) LIKE LOWER(:prefixLike) ESCAPE '\' THEN 1
                       ELSE 2
                   END AS match_rank
            FROM series s
            JOIN series_fts ON s.id = series_fts.rowid
            WHERE s.provider_id = :providerId
              AND series_fts MATCH :ftsQuery
            ORDER BY match_rank ASC, s.name ASC
            LIMIT :limitPerSection
        )
        ORDER BY section_rank ASC, match_rank ASC, title ASC
        """
    )
    abstract fun searchAll(
        providerId: Long,
        ftsQuery: String,
        rawQuery: String,
        prefixLike: String,
        limitPerSection: Int
    ): Flow<List<SearchHitEntity>>

    @Query(
        """
        SELECT 'LIVE' AS content_type,
               c.id AS content_id,
               c.name AS title,
               0 AS section_rank,
               CASE
                   WHEN LOWER(c.name) = LOWER(:rawQuery) THEN 0
                   WHEN LOWER(c.name) LIKE LOWER(:prefixLike) ESCAPE '\' THEN 1
                   ELSE 2
               END AS match_rank
        FROM channels c
        JOIN channels_fts ON c.id = channels_fts.rowid
        WHERE c.provider_id = :providerId
          AND channels_fts MATCH :ftsQuery
        ORDER BY match_rank ASC, c.name ASC
        LIMIT :limitPerSection
        """
    )
    abstract fun searchLive(
        providerId: Long,
        ftsQuery: String,
        rawQuery: String,
        prefixLike: String,
        limitPerSection: Int
    ): Flow<List<SearchHitEntity>>

    @Query(
        """
        SELECT 'MOVIE' AS content_type,
               m.id AS content_id,
               m.name AS title,
               1 AS section_rank,
               CASE
                   WHEN LOWER(m.name) = LOWER(:rawQuery) THEN 0
                   WHEN LOWER(m.name) LIKE LOWER(:prefixLike) ESCAPE '\' THEN 1
                   ELSE 2
               END AS match_rank
        FROM movies m
        JOIN movies_fts ON m.id = movies_fts.rowid
        WHERE m.provider_id = :providerId
          AND movies_fts MATCH :ftsQuery
        ORDER BY match_rank ASC, m.name ASC
        LIMIT :limitPerSection
        """
    )
    abstract fun searchMovies(
        providerId: Long,
        ftsQuery: String,
        rawQuery: String,
        prefixLike: String,
        limitPerSection: Int
    ): Flow<List<SearchHitEntity>>

    @Query(
        """
        SELECT 'SERIES' AS content_type,
               s.id AS content_id,
               s.name AS title,
               2 AS section_rank,
               CASE
                   WHEN LOWER(s.name) = LOWER(:rawQuery) THEN 0
                   WHEN LOWER(s.name) LIKE LOWER(:prefixLike) ESCAPE '\' THEN 1
                   ELSE 2
               END AS match_rank
        FROM series s
        JOIN series_fts ON s.id = series_fts.rowid
        WHERE s.provider_id = :providerId
          AND series_fts MATCH :ftsQuery
        ORDER BY match_rank ASC, s.name ASC
        LIMIT :limitPerSection
        """
    )
    abstract fun searchSeries(
        providerId: Long,
        ftsQuery: String,
        rawQuery: String,
        prefixLike: String,
        limitPerSection: Int
    ): Flow<List<SearchHitEntity>>
}
