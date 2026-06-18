package com.streamvault.data.remote.gemini

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.streamvault.domain.search.NaturalLanguageSearchHints
import com.streamvault.domain.search.NaturalLanguageSearchInterpreter
import com.streamvault.domain.search.NaturalLanguageSearchQueryBuilder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiNaturalLanguageSearchInterpreter @Inject constructor(
    okHttpClient: OkHttpClient,
    @Named("geminiApiKey") private val apiKey: String
) : NaturalLanguageSearchInterpreter {

    private val gson = Gson()
    private val client = okHttpClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    override suspend fun interpret(query: String): NaturalLanguageSearchHints {
        val normalized = query.trim()
        val conversational = NaturalLanguageSearchQueryBuilder.isConversational(normalized)
        if (!conversational || apiKey.isBlank()) {
            return NaturalLanguageSearchHints(isConversational = conversational)
        }

        return withContext(Dispatchers.IO) {
            runCatching { translateWithGemini(normalized) }
                .onFailure { error ->
                    Log.w(TAG, "Gemini natural search failed", error)
                }
                .getOrDefault(NaturalLanguageSearchHints(isConversational = conversational))
        }
    }

    private fun translateWithGemini(query: String): NaturalLanguageSearchHints {
        val prompt = buildString {
            append(
                "You are a media library search assistant. Given a conversational TV or movie " +
                    "search query, extract the most probable exact title and comma-separated search " +
                    "keywords. Respond ONLY with valid JSON in this shape: "
            )
            append("""{"probable_title": "...", "keywords": "word1, word2, ..."}""")
            append("\nQuery: ")
            append(query)
        }

        val requestBody = gson.toJson(
            JsonObject().apply {
                add(
                    "contents",
                    gson.toJsonTree(
                        listOf(
                            mapOf("parts" to listOf(mapOf("text" to prompt)))
                        )
                    )
                )
                add(
                    "generationConfig",
                    JsonObject().apply {
                        addProperty("temperature", 0.2)
                        addProperty("maxOutputTokens", 256)
                    }
                )
            }
        ).toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$GEMINI_URL?key=$apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Gemini HTTP ${response.code}")
                return NaturalLanguageSearchHints(isConversational = true)
            }

            val body = response.body?.string().orEmpty()
            val text = extractResponseText(body)
            return parseHints(text).copy(isConversational = true)
        }
    }

    private fun extractResponseText(body: String): String {
        if (body.isBlank()) return ""
        return runCatching {
            val root = JsonParser.parseString(body).asJsonObject
            root.getAsJsonArray("candidates")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?.firstOrNull()
                ?.asJsonObject
                ?.get("text")
                ?.asString
                .orEmpty()
        }.getOrDefault("")
    }

    private fun parseHints(text: String): NaturalLanguageSearchHints {
        if (text.isBlank()) return NaturalLanguageSearchHints()

        var cleaned = text.trim()
        FENCE_REGEX.find(cleaned)?.let { match ->
            cleaned = match.groupValues[1].trim()
        }

        val payload = runCatching {
            JsonParser.parseString(cleaned).asJsonObject
        }.getOrNull() ?: BRACE_REGEX.find(cleaned)?.let { match ->
            runCatching { JsonParser.parseString(match.value).asJsonObject }.getOrNull()
        } ?: return NaturalLanguageSearchHints()

        val probableTitle = payload.get("probable_title")?.asString?.trim().orEmpty()
        val keywords = payload.get("keywords")?.asString
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return NaturalLanguageSearchHints(
            probableTitle = probableTitle,
            keywords = keywords,
            isConversational = true
        )
    }

    private companion object {
        const val TAG = "GeminiSearch"
        const val GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val FENCE_REGEX = Regex("```(?:json)?\\s*(\\{.*?\\})\\s*```", RegexOption.DOT_MATCHES_ALL)
        val BRACE_REGEX = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL)
    }
}
