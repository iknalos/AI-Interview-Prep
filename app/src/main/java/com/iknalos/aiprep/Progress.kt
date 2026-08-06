package com.iknalos.aiprep

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Grades the user can give a card in study mode. */
enum class Grade(val label: String, val quality: Int) {
    AGAIN("Again", 2),
    HARD("Hard", 3),
    GOOD("Good", 4),
    EASY("Easy", 5)
}

/**
 * SM-2 spaced repetition.
 *
 * A failed card returns to the queue the same session (interval 0) rather than
 * being pushed a day out, which is what you want when cramming for interviews.
 */
object Sm2 {

    const val MAX_INTERVAL_DAYS = 365.0

    fun review(state: CardState, grade: Grade, todayEpochDay: Long): CardState {
        val q = grade.quality
        var ease = state.ease
        var reps = state.reps
        var lapses = state.lapses
        var interval: Double

        if (q < 3) {
            reps = 0
            lapses += 1
            ease = max(1.3, ease - 0.2)
            interval = 0.0
        } else {
            ease = max(1.3, ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)))
            interval = when (reps) {
                0 -> 1.0
                1 -> 6.0
                else -> max(1.0, state.intervalDays) * ease
            }
            // "Hard" should come back sooner than "Good" even on the same rep count.
            if (q == 3) interval = max(1.0, interval * 0.6)
            reps += 1
        }

        interval = min(interval, MAX_INTERVAL_DAYS)

        return state.copy(
            reps = reps,
            lapses = lapses,
            ease = ease,
            intervalDays = interval,
            dueEpochDay = todayEpochDay + ceil(interval).toLong(),
            lastGrade = grade.ordinal,
            seen = state.seen + 1,
            correct = state.correct + if (q >= 3) 1 else 0
        )
    }
}

/**
 * JSON-file backed progress store. Small enough that a full rewrite per change
 * is cheaper and far less fragile than pulling in a database.
 */
class ProgressStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.filesDir, "progress.json")

    fun load(): Progress {
        if (!file.exists()) return Progress()
        return try {
            json.decodeFromString(Progress.serializer(), file.readText())
        } catch (e: Exception) {
            // Never let a corrupt file brick the app; start fresh instead.
            Progress()
        }
    }

    fun save(progress: Progress) {
        try {
            file.writeText(json.encodeToString(Progress.serializer(), progress))
        } catch (e: Exception) {
            // Losing a write is survivable; crashing mid-review is not.
        }
    }
}

/** Pure helpers over Progress so the ViewModel stays readable. */
object Streaks {

    fun registerStudy(p: Progress, todayEpochDay: Long): Progress {
        if (p.lastStudyEpochDay == todayEpochDay) {
            return p.copy(reviewsToday = p.reviewsToday + 1)
        }
        val continued = p.lastStudyEpochDay == todayEpochDay - 1
        val streak = if (continued) p.streakDays + 1 else 1
        return p.copy(
            streakDays = streak,
            bestStreak = max(p.bestStreak, streak),
            lastStudyEpochDay = todayEpochDay,
            reviewsToday = 1
        )
    }

    /** Streak shown in the UI, expired if the user skipped a whole day. */
    fun currentStreak(p: Progress, todayEpochDay: Long): Int {
        if (p.lastStudyEpochDay == todayEpochDay || p.lastStudyEpochDay == todayEpochDay - 1) {
            return p.streakDays
        }
        return 0
    }

    fun reviewsToday(p: Progress, todayEpochDay: Long): Int =
        if (p.lastStudyEpochDay == todayEpochDay) p.reviewsToday else 0
}
