package com.rushy.app

import android.content.Context

class RecentChannelsStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun record(itemId: String) {
        if (itemId.isBlank()) return
        val updated = (listOf(itemId) + getIds().filter { it != itemId }).take(MAX_RECENT)
        prefs.edit().putString(KEY_IDS, updated.joinToString(SEPARATOR)).apply()
    }

    fun getIds(): List<String> =
        prefs.getString(KEY_IDS, null)
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun remove(itemId: String) {
        val updated = getIds().filter { it != itemId }
        prefs.edit().putString(KEY_IDS, updated.joinToString(SEPARATOR)).apply()
    }

    companion object {
        private const val PREFS_NAME = "rushy_recent_channels"
        private const val KEY_IDS = "ids"
        private const val SEPARATOR = ","
        private const val MAX_RECENT = 20

        @Volatile
        private var instance: RecentChannelsStore? = null

        fun getInstance(context: Context): RecentChannelsStore {
            return instance ?: synchronized(this) {
                instance ?: RecentChannelsStore(context).also { instance = it }
            }
        }
    }
}
