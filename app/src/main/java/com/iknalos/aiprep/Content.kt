package com.iknalos.aiprep

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Loads the question bank and lessons.
 *
 * Content comes from one of two places: the copies bundled in the APK, or a newer
 * over-the-air bundle downloaded from GitHub Pages. Bundled content is the floor,
 * so the app always works offline and on first launch, and OTA content wins whenever
 * its version is higher. That is what lets new questions arrive without an install.
 *
 * Cards are bundled one file per topic (`cards_<topic>.json`), so the bank grows by
 * dropping in another file with no code change here.
 */
class ContentRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    lateinit var cards: List<Card>
        private set
    lateinit var topics: List<Topic>
        private set
    lateinit var lessons: List<Lesson>
        private set

    /** Version baked into the APK, taken as the max across bundled topic files. */
    var bundledVersion: Int = 0
        private set

    /** Version actually in use, which may be an OTA bundle. */
    var activeVersion: Int = 0
        private set

    var usingRemoteContent: Boolean = false
        private set

    fun load(remote: ContentBundle? = null) {
        val bundledTopics = readBundledTopics()
        val bundledLessons = readBundledLessons()
        bundledVersion = readBundledContentVersion()

        val useRemote = remote != null && remote.contentVersion > bundledVersion
        val topicFiles = if (useRemote) remote!!.topics else bundledTopics
        val lessonList = if (useRemote) remote!!.lessons else bundledLessons

        usingRemoteContent = useRemote
        activeVersion = if (useRemote) remote!!.contentVersion else bundledVersion

        apply(topicFiles, lessonList)
    }

    private fun apply(topicFiles: List<TopicFile>, lessonList: List<Lesson>) {
        val all = ArrayList<Card>()
        for (file in topicFiles) {
            for (c in file.cards) {
                // Skip anything malformed rather than crashing on a bad remote card.
                if (c.options.size != 4 || c.answer !in 0..3) continue
                if (c.question.isBlank()) continue
                all.add(
                    Card(
                        id = c.id,
                        topicId = file.topicId,
                        topicName = file.topicName,
                        difficulty = Difficulty.from(c.difficulty),
                        question = c.question,
                        options = c.options,
                        answer = c.answer,
                        explanation = c.explanation,
                        modelAnswer = c.modelAnswer
                    )
                )
            }
        }
        // A duplicate id would collide in the progress map, so keep the first.
        cards = all.distinctBy { it.id }
        lessons = lessonList

        // Topic order follows lesson order so the curriculum reads sensibly, with any
        // topic that has cards but no lesson appended at the end.
        val counts = cards.groupingBy { it.topicId }.eachCount()
        val names = cards.associate { it.topicId to it.topicName }
        val ordered = ArrayList<Topic>()
        for (l in lessons) {
            val n = counts[l.topicId] ?: continue
            ordered.add(Topic(l.topicId, names[l.topicId] ?: l.title, n))
        }
        for ((id, n) in counts) {
            if (ordered.none { it.id == id }) ordered.add(Topic(id, names[id] ?: id, n))
        }
        topics = ordered
    }

    private fun readBundledTopics(): List<TopicFile> {
        val assets = context.assets
        val names = assets.list("")
            ?.filter { it.startsWith("cards_") && it.endsWith(".json") }
            ?.sorted()
            ?: emptyList()
        val out = ArrayList<TopicFile>()
        for (name in names) {
            try {
                val text = assets.open(name).bufferedReader().use { it.readText() }
                out.add(json.decodeFromString(TopicFile.serializer(), text))
            } catch (e: Exception) {
                // One bad bundled file should not take the whole bank down.
            }
        }
        return out
    }

    private fun readBundledLessons(): List<Lesson> = try {
        val text = context.assets.open("lessons.json").bufferedReader().use { it.readText() }
        json.decodeFromString(LessonsFile.serializer(), text).lessons
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * The bundled content version, written into assets at build time. Absent in a
     * local build, in which case 0 means any published OTA bundle is newer.
     */
    private fun readBundledContentVersion(): Int = try {
        val text = context.assets.open("content-version.txt")
            .bufferedReader().use { it.readText() }
        text.trim().toIntOrNull() ?: 0
    } catch (e: Exception) {
        0
    }

    fun lessonFor(topicId: String): Lesson? = lessons.firstOrNull { it.topicId == topicId }

    fun card(id: String): Card? = cards.firstOrNull { it.id == id }
}
