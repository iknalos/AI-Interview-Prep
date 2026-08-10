package com.iknalos.aiprep

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ---------- content as it appears in assets ---------- */

@Serializable
class CardJson(
    val id: String,
    val difficulty: String,
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String,
    val modelAnswer: String
)

@Serializable
class TopicFile(
    val topicId: String,
    val topicName: String,
    val cards: List<CardJson>
)

/**
 * A picture that ships as JSON.
 *
 * Flashcards are meant to be glanceable, and a lot of interview material is
 * shaped like a pipeline or a comparison rather than a paragraph. Rather than
 * bundle bitmaps - which would bloat the APK and cannot ride the OTA content
 * bundle - the common shapes are described structurally and drawn by the app,
 * so they scale, theme, and translate. `image` is still there as an escape
 * hatch for anything that genuinely needs pixels: base64 keeps it inside the
 * same JSON payload.
 *
 * Only the fields relevant to `type` are read; the rest stay empty.
 */
@Serializable
class FlashVisualJson(
    val type: String = "",
    val caption: String = "",
    /** flowchart: boxes drawn top to bottom with arrows between them. */
    val steps: List<String> = emptyList(),
    /** table: header row plus body rows, all cells as text. */
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    /** diagram / code: preformatted monospace text, newlines preserved. */
    val text: String = "",
    /** image: base64 PNG or JPEG bytes, no data: prefix. */
    val data: String = ""
)

@Serializable
class FlashCardJson(
    val id: String,
    val difficulty: String,
    val prompt: String,
    val visual: FlashVisualJson? = null,
    val options: List<String>,
    val answer: Int,
    val explanation: String
)

@Serializable
class FlashFile(
    val topicId: String,
    val topicName: String,
    val cards: List<FlashCardJson>
)

@Serializable
class LessonSection(
    val heading: String,
    val body: String
)

@Serializable
class Lesson(
    val topicId: String,
    val title: String,
    val subtitle: String,
    val readMinutes: Int = 8,
    val sections: List<LessonSection>
)

@Serializable
class LessonsFile(
    val lessons: List<Lesson>
)

@Serializable
class NewsItem(
    val id: String,
    val title: String,
    val summary: String = "",
    val source: String = "",
    val url: String = "",
    val published: String = "",
    val tags: List<String> = emptyList()
)

@Serializable
class NewsFeed(
    @SerialName("generated") val generated: String = "",
    val items: List<NewsItem> = emptyList()
)

/* ---------- runtime models ---------- */

enum class Difficulty(val label: String, val key: String) {
    EASY("Easy", "easy"),
    MEDIUM("Medium", "medium"),
    HARD("Hard", "hard");

    companion object {
        fun from(key: String): Difficulty =
            entries.firstOrNull { it.key == key.lowercase() } ?: MEDIUM
    }
}

data class Card(
    val id: String,
    val topicId: String,
    val topicName: String,
    val difficulty: Difficulty,
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String,
    val modelAnswer: String
)

data class Topic(
    val id: String,
    val name: String,
    val cardCount: Int
)

enum class VisualKind { FLOWCHART, TABLE, DIAGRAM, CODE, IMAGE }

data class FlashVisual(
    val kind: VisualKind,
    val caption: String = "",
    val steps: List<String> = emptyList(),
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val text: String = "",
    val imageData: String = ""
)

/** A two-way call: one prompt, two options, one of them right. */
data class FlashCard(
    val id: String,
    val topicId: String,
    val topicName: String,
    val difficulty: Difficulty,
    val prompt: String,
    val visual: FlashVisual?,
    val options: List<String>,
    val answer: Int,
    val explanation: String
)

/* ---------- persisted progress ---------- */

@Serializable
data class CardState(
    val reps: Int = 0,
    val lapses: Int = 0,
    val ease: Double = 2.5,
    val intervalDays: Double = 0.0,
    val dueEpochDay: Long = 0L,
    val lastGrade: Int = -1,
    val seen: Int = 0,
    val correct: Int = 0
) {
    /** A card is considered mastered once it has survived a few good reviews. */
    val mastered: Boolean get() = reps >= 3 && intervalDays >= 14.0
}

@Serializable
data class QuizRecord(
    val epochMillis: Long,
    val total: Int,
    val correct: Int,
    val topicIds: List<String> = emptyList(),
    val mode: String = "quiz"
)

@Serializable
data class Progress(
    val cards: Map<String, CardState> = emptyMap(),
    val quizzes: List<QuizRecord> = emptyList(),
    val streakDays: Int = 0,
    val bestStreak: Int = 0,
    val lastStudyEpochDay: Long = 0L,
    val reviewsToday: Int = 0,
    val dailyGoal: Int = 20,
    val lessonsRead: Set<String> = emptySet(),
    val selectedTopics: Set<String> = emptySet(),
    val selectedDifficulties: Set<String> = emptySet()
)
