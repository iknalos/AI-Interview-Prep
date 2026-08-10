package com.iknalos.aiprep

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

@Serializable
class ContentIndex(
    val contentVersion: Int = 0,
    val generated: String = "",
    val cardCount: Int = 0,
    val topicCount: Int = 0,
    val lessonCount: Int = 0,
    val flashCount: Int = 0,
    val sha256: String = "",
    val url: String = ""
)

@Serializable
class ContentBundle(
    val contentVersion: Int = 0,
    val generated: String = "",
    val topics: List<TopicFile> = emptyList(),
    val lessons: List<Lesson> = emptyList(),
    /** Absent in bundles published before flashcards shipped; the app falls back. */
    val flashcards: List<FlashFile> = emptyList()
)

/**
 * Over-the-air question bank.
 *
 * The APK ships a copy of every card and lesson so the app works offline and on
 * first launch, but that copy is only a floor. A small index file is polled to see
 * whether anything changed, and the full bundle is downloaded only when the version
 * actually moved. That means new questions reach the phone without anyone installing
 * an APK, which is the whole point.
 */
class ContentSync(private val context: Context) {

    companion object {
        const val INDEX_URL = "https://iknalos.github.io/AI-Interview-Prep/content-index.json"
        private const val CACHE_FILE = "content_cache.json"
        private const val TIMEOUT_MS = 20_000

        /** Guards against a corrupt or hostile payload replacing good content. */
        private const val MIN_CARDS = 50
        private const val MIN_TOPICS = 4
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = File(context.filesDir, CACHE_FILE)

    /** Version of the content currently cached on disk, 0 if there is none. */
    fun cachedVersion(): Int = cached()?.contentVersion ?: 0

    fun cached(): ContentBundle? {
        if (!cache.exists()) return null
        return try {
            val bundle = json.decodeFromString(ContentBundle.serializer(), cache.readText())
            if (isSane(bundle)) bundle else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isSane(bundle: ContentBundle): Boolean {
        val cards = bundle.topics.sumOf { it.cards.size }
        return bundle.topics.size >= MIN_TOPICS &&
            cards >= MIN_CARDS &&
            bundle.lessons.isNotEmpty() &&
            // Every topic with cards needs a lesson, or topic ordering breaks.
            bundle.topics.all { t -> bundle.lessons.any { it.topicId == t.topicId } }
    }

    /**
     * Blocking. Returns the newly downloaded bundle, or null if nothing was newer,
     * the network failed, or the payload failed validation.
     */
    fun sync(bundledVersion: Int): ContentBundle? {
        val index = fetchIndex() ?: return null
        val have = maxOf(cachedVersion(), bundledVersion)
        if (index.contentVersion <= have) return null

        // Hashed as raw bytes, exactly as the publisher hashed them, so no text
        // decode/re-encode round trip can shift the digest.
        val bytes = fetchBytes(index.url.ifBlank { return null }) ?: return null

        // Verify before trusting: a truncated download is worse than no download.
        if (index.sha256.isNotBlank()) {
            val actual = sha256(bytes)
            if (!actual.equals(index.sha256, ignoreCase = true)) return null
        }

        val body = try {
            bytes.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }

        val bundle = try {
            json.decodeFromString(ContentBundle.serializer(), body)
        } catch (e: Exception) {
            return null
        }

        if (!isSane(bundle) || bundle.contentVersion <= have) return null

        return try {
            cache.writeText(body)
            bundle
        } catch (e: Exception) {
            // Could not persist, but the parsed bundle is still usable this session.
            bundle
        }
    }

    fun fetchIndex(): ContentIndex? {
        val text = fetchText(INDEX_URL) ?: return null
        return try {
            json.decodeFromString(ContentIndex.serializer(), text)
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchText(url: String): String? =
        fetchBytes(url)?.toString(Charsets.UTF_8)

    private fun fetchBytes(url: String): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append("%02x".format(b))
        return sb.toString()
    }

    fun clearCache() {
        try {
            if (cache.exists()) cache.delete()
        } catch (e: Exception) {
            // Nothing useful to do; the sane-check will reject bad content anyway.
        }
    }
}
