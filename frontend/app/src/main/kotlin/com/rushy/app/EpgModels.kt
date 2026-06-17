package com.rushy.app

data class EpgProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startEpochSec: Long,
    val endEpochSec: Long,
)

data class ChannelCategory(
    val id: String,
    val name: String,
)

data class EpgChannelRow(
    val channel: MediaItem,
    val programs: List<EpgProgram>,
)

data class CategoryGroup(
    val category: ChannelCategory,
    val channels: List<MediaItem>,
)
