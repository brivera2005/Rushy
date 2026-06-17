package com.rushy.app

import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

object XmltvParser {
    private const val TAG = "XmltvParser"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun downloadXmltv(
        portalUrl: String,
        username: String,
        password: String,
        destFile: File,
        onProgress: (String) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val base = XtreamClient.normalizePortalUrl(portalUrl).trimEnd('/')
        val url = "$base/xmltv.php?username=$username&password=$password"
        onProgress("Downloading TV guide...")
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Rushy/1.2.0 (Android TV)")
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "xmltv download failed: HTTP ${response.code}")
                return@withContext false
            }
            val body = response.body ?: return@withContext false
            destFile.parentFile?.mkdirs()
            destFile.outputStream().use { out ->
                body.byteStream().use { input -> input.copyTo(out) }
            }
            onProgress("Guide downloaded (${destFile.length() / 1024 / 1024} MB)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "xmltv download error", e)
            false
        }
    }

    /**
     * Stream-parse XMLTV and return programmes for the requested channel IDs.
     * Only keeps entries overlapping [windowStart, windowEnd].
     */
    suspend fun parseProgrammes(
        file: File,
        channelIds: Set<String>,
        windowStartSec: Long,
        windowEndSec: Long,
        onProgress: (Int) -> Unit = {},
    ): List<EpgProgramEntity> = withContext(Dispatchers.IO) {
        if (!file.exists() || channelIds.isEmpty()) return@withContext emptyList()

        val results = ArrayList<EpgProgramEntity>(4096)
        var parsed = 0

        val inputStream = when {
            file.name.endsWith(".gz") -> GZIPInputStream(FileInputStream(file))
            else -> FileInputStream(file)
        }

        inputStream.use { stream ->
            val parser = Xml.newPullParser()
            parser.setInput(stream, "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                    val channel = parser.getAttributeValue(null, "channel") ?: ""
                    val startAttr = parser.getAttributeValue(null, "start")
                    val stopAttr = parser.getAttributeValue(null, "stop")
                    val startSec = parseXmltvTime(startAttr)
                    val endSec = parseXmltvTime(stopAttr)
                    val inWindow = startSec != null && endSec != null &&
                        endSec > windowStartSec && startSec < windowEndSec

                    if (channelIds.contains(channel) && inWindow) {
                        var title = ""
                        var desc: String? = null
                        var depth = 1
                        event = parser.next()
                        while (depth > 0) {
                            when (event) {
                                XmlPullParser.START_TAG -> {
                                    depth++
                                    when (parser.name) {
                                        "title" -> title = parser.nextText().trim().also { depth-- }
                                        "desc" -> desc = parser.nextText().trim().also { depth-- }
                                    }
                                }
                                XmlPullParser.END_TAG -> depth--
                            }
                            if (depth > 0) event = parser.next()
                        }
                        if (title.isNotBlank()) {
                            results.add(
                                EpgProgramEntity(
                                    id = "${channel}_${startSec}",
                                    channelId = channel,
                                    title = decodeEntities(title),
                                    description = desc?.let { decodeEntities(it) },
                                    startEpochSec = startSec!!,
                                    endEpochSec = endSec!!,
                                ),
                            )
                            parsed++
                            if (parsed % 500 == 0) onProgress(parsed)
                        }
                    } else {
                        var depth = 1
                        event = parser.next()
                        while (depth > 0) {
                            if (event == XmlPullParser.START_TAG) depth++
                            if (event == XmlPullParser.END_TAG) depth--
                            if (depth > 0) event = parser.next()
                        }
                    }
                    event = parser.eventType
                    continue
                }
                event = parser.next()
            }
        }
        onProgress(parsed)
        results
    }

    private fun parseXmltvTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val trimmed = raw.trim()
            val spaceIdx = trimmed.indexOf(' ')
            val digits = if (spaceIdx > 0) trimmed.substring(0, spaceIdx) else trimmed
            val tzPart = if (spaceIdx > 0) trimmed.substring(spaceIdx + 1) else "+0000"
            if (digits.length < 14) return null
            val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
            format.parse("${digits.substring(0, 14)} $tzPart")?.time?.div(1000)
        }.getOrNull()
    }

    private fun decodeEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }
}
