package com.iknalos.aiprep

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Daily AI news, published as a static JSON file by a GitHub Actions workflow in
 * this repo. Keeping the aggregation server-side means no API keys in the app and
 * a feed that can be fixed without shipping a release.
 */
class NewsRepository(private val context: Context) {

    companion object {
        const val FEED_URL = "https://iknalos.github.io/AI-Interview-Prep/news.json"
        private const val CACHE_FILE = "news_cache.json"
        private const val TIMEOUT_MS = 12_000
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = File(context.filesDir, CACHE_FILE)

    /** Bundled snapshot so the very first launch is never an empty screen. */
    fun bundled(): NewsFeed = try {
        val text = context.assets.open("news.json").bufferedReader().use { it.readText() }
        json.decodeFromString(NewsFeed.serializer(), text)
    } catch (e: Exception) {
        NewsFeed()
    }

    fun cached(): NewsFeed? {
        if (!cache.exists()) return null
        return try {
            json.decodeFromString(NewsFeed.serializer(), cache.readText())
        } catch (e: Exception) {
            null
        }
    }

    /** Blocking; callers run this on a background dispatcher. */
    fun fetch(): NewsFeed? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(FEED_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val feed = json.decodeFromString(NewsFeed.serializer(), text)
            if (feed.items.isEmpty()) return null
            try {
                cache.writeText(text)
            } catch (e: Exception) {
                // A failed cache write shouldn't fail the fetch.
            }
            feed
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
