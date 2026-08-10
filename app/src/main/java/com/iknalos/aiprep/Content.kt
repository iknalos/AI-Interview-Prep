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
 * dropping in another file with no code change here. Flashcards follow the same
 * convention in `flash_<topic>.json`.
 */
class ContentRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    lateinit var cards: List<Card>
        private set
    lateinit var flashCards: List<FlashCard>
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
        val bundledFlash = readBundledFlash()
        bundledVersion = readBundledContentVersion()

        val useRemote = remote != null && remote.contentVersion > bundledVersion
        val topicFiles = if (useRemote) remote!!.topics else bundledTopics
        val lessonList = if (useRemote) remote!!.lessons else bundledLessons
        // A bundle published before flashcards existed carries none. Falling back to
        // the bundled set keeps the mode alive instead of emptying it on update.
        val remoteFlash = remote?.flashcards ?: emptyList()
        val flashFiles = if (useRemote && remoteFlash.isNotEmpty()) remoteFlash else bundledFlash

        usingRemoteContent = useRemote
        activeVersion = if (useRemote) remote!!.contentVersion else bundledVersion

        apply(topicFiles, lessonList, flashFiles)
    }

    private fun apply(
        topicFiles: List<TopicFile>,
        lessonList: List<Lesson>,
        flashFiles: List<FlashFile>
    ) {
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

        val flash = ArrayList<FlashCard>()
        for (file in flashFiles) {
            for (c in file.cards) {
                // Two options, exactly one right: that is the whole premise of the mode.
                if (c.options.size != 2 || c.answer !in 0..1) continue
                if (c.prompt.isBlank() || c.options.any { it.isBlank() }) continue
                flash.add(
                    FlashCard(
                        id = c.id,
                        topicId = file.topicId,
                        topicName = file.topicName,
                        difficulty = Difficulty.from(c.difficulty),
                        prompt = c.prompt,
                        visual = visualOf(c.visual),
                        options = c.options,
                        answer = c.answer,
                        explanation = c.explanation
                    )
                )
            }
        }
        flashCards = flash.distinctBy { it.id }

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

    /**
     * A visual is decoration, never the question itself, so anything malformed is
     * dropped rather than allowed to fail the card. The prompt still stands alone.
     */
    private fun visualOf(v: FlashVisualJson?): FlashVisual? {
        if (v == null) return null
        val kind = when (v.type.lowercase()) {
            "flowchart" -> VisualKind.FLOWCHART
            "table" -> VisualKind.TABLE
            "diagram" -> VisualKind.DIAGRAM
            "code" -> VisualKind.CODE
            "image" -> VisualKind.IMAGE
            else -> return null
        }
        val complete = when (kind) {
            VisualKind.FLOWCHART -> v.steps.size >= 2
            VisualKind.TABLE -> v.headers.isNotEmpty() && v.rows.isNotEmpty()
            VisualKind.DIAGRAM, VisualKind.CODE -> v.text.isNotBlank()
            VisualKind.IMAGE -> v.data.isNotBlank()
        }
        if (!complete) return null
        return FlashVisual(
            kind = kind,
            caption = v.caption,
            steps = v.steps,
            headers = v.headers,
            rows = v.rows,
            text = v.text,
            imageData = v.data
        )
    }

    private fun readBundledFlash(): List<FlashFile> {
        val assets = context.assets
        val names = assets.list("")
            ?.filter { it.startsWith("flash_") && it.endsWith(".json") }
            ?.sorted()
            ?: emptyList()
        val out = ArrayList<FlashFile>()
        for (name in names) {
            try {
                val text = assets.open(name).bufferedReader().use { it.readText() }
                out.add(json.decodeFromString(FlashFile.serializer(), text))
            } catch (e: Exception) {
                // Same reasoning as the card bank: one bad file is not worth a crash.
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

    fun flashCard(id: String): FlashCard? = flashCards.firstOrNull { it.id == id }
}
