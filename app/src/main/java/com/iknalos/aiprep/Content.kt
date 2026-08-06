package com.iknalos.aiprep

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Loads the bundled question bank and lessons out of assets.
 *
 * Cards live in one file per topic (`cards_<topic>.json`) so the bank can grow
 * by dropping in another file, with no code change here.
 */
class ContentRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    lateinit var cards: List<Card>
        private set
    lateinit var topics: List<Topic>
        private set
    lateinit var lessons: List<Lesson>
        private set

    fun load() {
        val assets = context.assets
        val cardFiles = assets.list("")
            ?.filter { it.startsWith("cards_") && it.endsWith(".json") }
            ?.sorted()
            ?: emptyList()

        val all = ArrayList<Card>()
        for (name in cardFiles) {
            val text = assets.open(name).bufferedReader().use { it.readText() }
            val file = json.decodeFromString(TopicFile.serializer(), text)
            for (c in file.cards) {
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
        cards = all

        val lessonsText = assets.open("lessons.json").bufferedReader().use { it.readText() }
        lessons = json.decodeFromString(LessonsFile.serializer(), lessonsText).lessons

        // Topic order follows the lesson order so the curriculum reads sensibly,
        // with any topic that has cards but no lesson appended at the end.
        val counts = all.groupingBy { it.topicId }.eachCount()
        val names = all.associate { it.topicId to it.topicName }
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

    fun lessonFor(topicId: String): Lesson? = lessons.firstOrNull { it.topicId == topicId }

    fun card(id: String): Card? = cards.firstOrNull { it.id == id }
}
