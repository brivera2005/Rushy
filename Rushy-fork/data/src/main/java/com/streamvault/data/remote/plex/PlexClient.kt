package com.streamvault.data.remote.plex

import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class PlexMediaContainer<T>(
    @SerializedName("MediaContainer") val mediaContainer: T? = null,
)

data class PlexDirectoryList(
    @SerializedName("Directory") val directories: List<PlexDirectory>? = null,
)

data class PlexDirectory(
    @SerializedName("key") val key: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("type") val type: String? = null,
)

data class PlexMetadataList(
    @SerializedName("Metadata") val metadata: List<PlexMetadata>? = null,
)

data class PlexMetadata(
    @SerializedName("ratingKey") val ratingKey: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("thumb") val thumb: String? = null,
    @SerializedName("type") val type: String? = null,
)

interface PlexApiService {
    @GET("library/sections")
    suspend fun getLibrarySections(): PlexMediaContainer<PlexDirectoryList>

    @GET("library/sections/{sectionId}/all")
    suspend fun getSectionItems(
        @Path("sectionId") sectionId: String,
    ): PlexMediaContainer<PlexMetadataList>
}

@Singleton
class PlexClient @Inject constructor() {
    fun forCredentials(serverUrl: String, token: String): PlexClientSession {
        return PlexClientSession(normalizeServerUrl(serverUrl), token)
    }

    class PlexClientSession(
        serverUrl: String,
        private val token: String,
    ) {
        private val baseUrl: String = serverUrl
        private val api: PlexApiService

        init {
            val authInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-Plex-Token", token)
                    .header("Accept", "application/json")
                    .header("X-Plex-Client-Identifier", CLIENT_ID)
                    .header("X-Plex-Product", "Rushy")
                    .header("X-Plex-Version", "1.0")
                    .build()
                chain.proceed(request)
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .build()

            api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PlexApiService::class.java)
        }

        suspend fun validateCredentials(): Boolean {
            val response = api.getLibrarySections()
            return !response.mediaContainer?.directories.isNullOrEmpty()
        }

        suspend fun librarySectionCount(): Int =
            api.getLibrarySections().mediaContainer?.directories?.size ?: 0
    }

    companion object {
        private const val CLIENT_ID = "rushy-streamvault-fork"

        fun normalizeServerUrl(url: String): String {
            var normalized = url.trim().removeSuffix("/")
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                normalized = "http://$normalized"
            }
            if (!normalized.endsWith("/")) {
                normalized = "$normalized/"
            }
            return normalized
        }
    }
}
