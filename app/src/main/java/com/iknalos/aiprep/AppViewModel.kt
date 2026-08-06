package com.iknalos.aiprep

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt

/* ---------- session state ---------- */

data class StudySession(
    val queue: List<Card>,
    val index: Int = 0,
    val revealed: Boolean = false,
    val graded: Int = 0,
    val startedWith: Int = queue.size
) {
    val current: Card? get() = queue.getOrNull(index)
    val done: Boolean get() = index >= queue.size
}

data class QuizSession(
    val cards: List<Card>,
    val index: Int = 0,
    val chosen: List<Int> = List(cards.size) { -1 },
    val locked: Boolean = false,
    val finished: Boolean = false
) {
    val current: Card? get() = cards.getOrNull(index)
    val correctCount: Int get() = cards.indices.count { chosen[it] == cards[it].answer }
}

/** Self-graded open-ended practice: 0 missed, 1 partial, 2 solid. */
data class MockSession(
    val cards: List<Card>,
    val index: Int = 0,
    val revealed: Boolean = false,
    val grades: List<Int> = List(cards.size) { -1 },
    val finished: Boolean = false
) {
    val current: Card? get() = cards.getOrNull(index)
    val scored: Int get() = grades.count { it >= 0 }
    val points: Int get() = grades.filter { it >= 0 }.sum()
}

data class NewsState(
    val feed: NewsFeed = NewsFeed(),
    val loading: Boolean = false,
    val offline: Boolean = false
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val contentRepo = ContentRepository(app)
    private val progressStore = ProgressStore(app)
    private val newsRepo = NewsRepository(app)

    var ready by mutableStateOf(false)
        private set
    var progress by mutableStateOf(Progress())
        private set

    var study by mutableStateOf<StudySession?>(null)
        private set
    var quiz by mutableStateOf<QuizSession?>(null)
        private set
    var mock by mutableStateOf<MockSession?>(null)
        private set
    var news by mutableStateOf(NewsState())
        private set

    /** Last session summary, so a results screen survives navigation. */
    var lastResult by mutableStateOf<QuizRecord?>(null)
        private set

    val topics: List<Topic> get() = if (ready) contentRepo.topics else emptyList()
    val allCards: List<Card> get() = if (ready) contentRepo.cards else emptyList()
    val lessons: List<Lesson> get() = if (ready) contentRepo.lessons else emptyList()

    val today: Long get() = LocalDate.now().toEpochDay()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                contentRepo.load()
                progressStore.load()
            }
            progress = loaded
            ready = true

            val seed = withContext(Dispatchers.IO) { newsRepo.cached() ?: newsRepo.bundled() }
            news = news.copy(feed = seed)
            refreshNews()
        }
    }

    /* ---------- persistence ---------- */

    private fun update(block: (Progress) -> Progress) {
        val next = block(progress)
        progress = next
        viewModelScope.launch(Dispatchers.IO) { progressStore.save(next) }
    }

    fun setDailyGoal(goal: Int) = update { it.copy(dailyGoal = goal.coerceIn(5, 200)) }

    fun markLessonRead(topicId: String) =
        update { it.copy(lessonsRead = it.lessonsRead + topicId) }

    fun resetProgress() = update {
        Progress(
            dailyGoal = it.dailyGoal,
            selectedTopics = it.selectedTopics,
            selectedDifficulties = it.selectedDifficulties
        )
    }

    /* ---------- filters ---------- */

    fun toggleTopic(id: String) = update {
        val s = it.selectedTopics
        it.copy(selectedTopics = if (id in s) s - id else s + id)
    }

    fun toggleDifficulty(key: String) = update {
        val s = it.selectedDifficulties
        it.copy(selectedDifficulties = if (key in s) s - key else s + key)
    }

    fun clearFilters() = update {
        it.copy(selectedTopics = emptySet(), selectedDifficulties = emptySet())
    }

    fun selectAllTopics() = update { it.copy(selectedTopics = topics.map { t -> t.id }.toSet()) }

    /** Empty selection means "everything", which keeps first launch useful. */
    fun filteredCards(): List<Card> {
        val t = progress.selectedTopics
        val d = progress.selectedDifficulties
        return allCards.filter { c ->
            (t.isEmpty() || c.topicId in t) && (d.isEmpty() || c.difficulty.key in d)
        }
    }

    fun stateOf(cardId: String): CardState = progress.cards[cardId] ?: CardState()

    /* ---------- study (spaced repetition) ---------- */

    fun dueCards(): List<Card> {
        val t = today
        return filteredCards().filter { c ->
            val s = progress.cards[c.id]
            s != null && s.dueEpochDay <= t
        }
    }

    fun newCards(): List<Card> = filteredCards().filter { progress.cards[it.id] == null }

    fun startStudy(limit: Int = 20) {
        val due = dueCards().sortedBy { stateOf(it.id).dueEpochDay }
        val fresh = newCards().shuffled()
        // Due material first: forgetting something you already learned costs more
        // than not having seen a new card yet.
        val queue = (due + fresh).take(limit.coerceAtLeast(1))
        study = if (queue.isEmpty()) null else StudySession(queue)
    }

    fun revealStudy() {
        study?.let { study = it.copy(revealed = true) }
    }

    fun gradeStudy(grade: Grade) {
        val s = study ?: return
        val card = s.current ?: return
        val todayVal = today

        update { p ->
            val next = Sm2.review(p.cards[card.id] ?: CardState(), grade, todayVal)
            Streaks.registerStudy(p.copy(cards = p.cards + (card.id to next)), todayVal)
        }

        // A lapsed card goes to the back of this session's queue rather than waiting a day.
        val requeue = grade == Grade.AGAIN
        val newQueue = if (requeue) s.queue + card else s.queue
        study = s.copy(queue = newQueue, index = s.index + 1, revealed = false, graded = s.graded + 1)
    }

    fun endStudy() {
        study = null
    }

    /* ---------- quiz ---------- */

    fun startQuiz(count: Int = 10) {
        val pool = filteredCards()
        if (pool.isEmpty()) {
            quiz = null
            return
        }
        val picked = pool.shuffled().take(count.coerceIn(1, pool.size))
        quiz = QuizSession(picked)
    }

    fun answerQuiz(optionIndex: Int) {
        val q = quiz ?: return
        if (q.locked || q.finished) return
        val card = q.current ?: return
        val chosen = q.chosen.toMutableList()
        chosen[q.index] = optionIndex
        quiz = q.copy(chosen = chosen, locked = true)

        // Quiz answers also feed the review schedule, so drilling isn't wasted effort.
        val correct = optionIndex == card.answer
        val todayVal = today
        update { p ->
            val prev = p.cards[card.id] ?: CardState()
            val graded = Sm2.review(prev, if (correct) Grade.GOOD else Grade.AGAIN, todayVal)
            Streaks.registerStudy(p.copy(cards = p.cards + (card.id to graded)), todayVal)
        }
    }

    fun nextQuiz() {
        val q = quiz ?: return
        if (q.index + 1 >= q.cards.size) {
            val record = QuizRecord(
                epochMillis = System.currentTimeMillis(),
                total = q.cards.size,
                correct = q.correctCount,
                topicIds = q.cards.map { it.topicId }.distinct(),
                mode = "quiz"
            )
            lastResult = record
            update { it.copy(quizzes = (it.quizzes + record).takeLast(200)) }
            quiz = q.copy(finished = true)
        } else {
            quiz = q.copy(index = q.index + 1, locked = false)
        }
    }

    fun endQuiz() {
        quiz = null
    }

    /* ---------- mock interview ---------- */

    fun startMock(count: Int = 5) {
        val pool = filteredCards()
        if (pool.isEmpty()) {
            mock = null
            return
        }
        // Bias toward harder questions: a mock loop should stretch you.
        val hard = pool.filter { it.difficulty != Difficulty.EASY }
        val source = if (hard.size >= count) hard else pool
        mock = MockSession(source.shuffled().take(count.coerceIn(1, source.size)))
    }

    fun revealMock() {
        mock?.let { mock = it.copy(revealed = true) }
    }

    fun gradeMock(points: Int) {
        val m = mock ?: return
        val card = m.current ?: return
        val grades = m.grades.toMutableList()
        grades[m.index] = points.coerceIn(0, 2)

        val todayVal = today
        val grade = when (points) {
            0 -> Grade.AGAIN
            1 -> Grade.HARD
            else -> Grade.GOOD
        }
        update { p ->
            val next = Sm2.review(p.cards[card.id] ?: CardState(), grade, todayVal)
            Streaks.registerStudy(p.copy(cards = p.cards + (card.id to next)), todayVal)
        }

        if (m.index + 1 >= m.cards.size) {
            val record = QuizRecord(
                epochMillis = System.currentTimeMillis(),
                total = m.cards.size * 2,
                correct = grades.filter { it >= 0 }.sum(),
                topicIds = m.cards.map { it.topicId }.distinct(),
                mode = "mock"
            )
            lastResult = record
            update { it.copy(quizzes = (it.quizzes + record).takeLast(200)) }
            mock = m.copy(grades = grades, finished = true)
        } else {
            mock = m.copy(grades = grades, index = m.index + 1, revealed = false)
        }
    }

    fun endMock() {
        mock = null
    }

    /* ---------- news ---------- */

    fun refreshNews() {
        if (news.loading) return
        news = news.copy(loading = true)
        viewModelScope.launch {
            val fetched = withContext(Dispatchers.IO) { newsRepo.fetch() }
            news = if (fetched != null) {
                NewsState(feed = fetched, loading = false, offline = false)
            } else {
                news.copy(loading = false, offline = true)
            }
        }
    }

    /* ---------- stats ---------- */

    data class TopicStat(
        val topic: Topic,
        val seen: Int,
        val mastered: Int,
        val accuracy: Int,
        val due: Int
    )

    fun topicStats(): List<TopicStat> {
        val t = today
        return topics.map { topic ->
            val cardsIn = allCards.filter { it.topicId == topic.id }
            val states = cardsIn.mapNotNull { progress.cards[it.id] }
            val attempts = states.sumOf { it.seen }
            val hits = states.sumOf { it.correct }
            TopicStat(
                topic = topic,
                seen = states.count { it.seen > 0 },
                mastered = states.count { it.mastered },
                accuracy = if (attempts == 0) 0 else (100.0 * hits / attempts).roundToInt(),
                due = cardsIn.count { c -> progress.cards[c.id]?.let { it.dueEpochDay <= t } == true }
            )
        }
    }

    fun overallAccuracy(): Int {
        val attempts = progress.cards.values.sumOf { it.seen }
        val hits = progress.cards.values.sumOf { it.correct }
        return if (attempts == 0) 0 else (100.0 * hits / attempts).roundToInt()
    }

    fun masteredCount(): Int = progress.cards.values.count { it.mastered }

    fun streak(): Int = Streaks.currentStreak(progress, today)

    fun reviewsToday(): Int = Streaks.reviewsToday(progress, today)
}
