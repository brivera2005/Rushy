package com.streamvault.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.streamvault.domain.model.ContentType

@Entity(
    tableName = "xtream_content_index",
    primaryKeys = ["provider_id", "content_type", "remote_id"],
    foreignKeys = [ForeignKey(
        entity = ProviderEntity::class,
        parentColumns = ["id"],
        childColumns = ["provider_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["provider_id", "content_type"]),
        Index(value = ["provider_id", "content_type", "category_id"]),
        Index(value = ["provider_id", "content_type", "name"]),
        Index(value = ["provider_id", "content_type", "local_content_id"]),
        Index(value = ["provider_id", "indexed_at"]),
        Index(value = ["stale_state"])
    ]
)
data class XtreamContentIndexEntity(
    @ColumnInfo(name = "provider_id") val providerId: Long,
    @ColumnInfo(name = "content_type") val contentType: ContentType,
    @ColumnInfo(name = "remote_id") val remoteId: String,
    @ColumnInfo(name = "local_content_id") val localContentId: Long? = null,
    val name: String,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "category_name") val categoryName: String? = null,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    @ColumnInfo(name = "container_extension") val containerExtension: String? = null,
    val rating: Float = 0f,
    @ColumnInfo(name = "added_at") val addedAt: Long = 0L,
    @ColumnInfo(name = "remote_updated_at") val remoteUpdatedAt: Long = 0L,
    @ColumnInfo(name = "is_adult") val isAdult: Boolean = false,
    @ColumnInfo(name = "indexed_at") val indexedAt: Long = 0L,
    @ColumnInfo(name = "detail_hydrated_at") val detailHydratedAt: Long = 0L,
    @ColumnInfo(name = "stale_state") val staleState: String = "ACTIVE",
    @ColumnInfo(name = "error_state") val errorState: String? = null,
    @ColumnInfo(name = "sync_fingerprint") val syncFingerprint: String = ""
)

@Entity(
    tableName = "xtream_index_jobs",
    primaryKeys = ["provider_id", "section"],
    foreignKeys = [ForeignKey(
        entity = ProviderEntity::class,
        parentColumns = ["id"],
        childColumns = ["provider_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["provider_id"]),
        Index(value = ["section"]),
        Index(value = ["state"]),
        Index(value = ["updated_at"])
    ]
)
data class XtreamIndexJobEntity(
    @ColumnInfo(name = "provider_id") val providerId: Long,
    val section: String,
    val state: String = "IDLE",
    @ColumnInfo(name = "total_categories") val totalCategories: Int = 0,
    @ColumnInfo(name = "completed_categories") val completedCategories: Int = 0,
    @ColumnInfo(name = "next_category_index") val nextCategoryIndex: Int = 0,
    @ColumnInfo(name = "failed_categories") val failedCategories: Int = 0,
    @ColumnInfo(name = "indexed_rows") val indexedRows: Int = 0,
    @ColumnInfo(name = "skipped_malformed_rows") val skippedMalformedRows: Int = 0,
    @ColumnInfo(name = "deleted_pruned_rows") val deletedPrunedRows: Int = 0,
    @ColumnInfo(name = "priority_category_id") val priorityCategoryId: Long? = null,
    @ColumnInfo(name = "priority_requested_at") val priorityRequestedAt: Long = 0L,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long = 0L,
    @ColumnInfo(name = "last_success_at") val lastSuccessAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L
)
