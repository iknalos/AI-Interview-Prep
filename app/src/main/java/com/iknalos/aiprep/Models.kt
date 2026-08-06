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
