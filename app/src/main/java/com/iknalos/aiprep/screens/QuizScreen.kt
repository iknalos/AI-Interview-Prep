package com.iknalos.aiprep.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iknalos.aiprep.AppViewModel
import com.iknalos.aiprep.ui.Bar
import com.iknalos.aiprep.ui.ColSpacer
import com.iknalos.aiprep.ui.Cyan
import com.iknalos.aiprep.ui.EmptyState
import com.iknalos.aiprep.ui.Good
import com.iknalos.aiprep.ui.OutlineButton
import com.iknalos.aiprep.ui.Panel
import com.iknalos.aiprep.ui.PrimaryButton
import com.iknalos.aiprep.ui.RowSpacer
import com.iknalos.aiprep.ui.SectionTitle
import com.iknalos.aiprep.ui.StatTile
import com.iknalos.aiprep.ui.Tag
import com.iknalos.aiprep.ui.difficultyColor
import kotlin.math.roundToInt

@Composable
fun QuizScreen(vm: AppViewModel, onExit: () -> Unit) {
    val session = vm.quiz

    if (session == null) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Scored quiz", style = MaterialTheme.typography.headlineSmall)
            ColSpacer(6)
            Text(
                "Multiple choice against the clock in your head. Answers still feed your review " +
                    "schedule, so a quiz is never wasted practice.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColSpacer(20)
            Row(Modifier.fillMaxWidth()) {
                PrimaryButton("10", { vm.startQuiz(10) }, Modifier.weight(1f))
                RowSpacer(8)
                PrimaryButton("20", { vm.startQuiz(20) }, Modifier.weight(1f))
                RowSpacer(8)
                PrimaryButton("40", { vm.startQuiz(40) }, Modifier.weight(1f))
            }
            ColSpacer(12)
            Text(
                "Drawn from ${vm.filteredCards().size} cards in your current focus",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val history = vm.progress.quizzes.filter { it.mode == "quiz" }.takeLast(5).reversed()
            if (history.isNotEmpty()) {
                ColSpacer(24)
                SectionTitle("RECENT QUIZZES")
                ColSpacer(8)
                history.forEach { r ->
                    val pct = if (r.total == 0) 0 else (100.0 * r.correct / r.total).roundToInt()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${r.correct}/${r.total}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "$pct%",
                            style = MaterialTheme.typography.labelLarge,
                            color = accuracyColor(pct)
                        )
                    }
                }
            }
        }
        return
    }

    if (session.finished) {
        val pct = if (session.cards.isEmpty()) 0
        else (100.0 * session.correctCount / session.cards.size).roundToInt()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            ColSpacer(20)
            Text("Quiz complete", style = MaterialTheme.typography.headlineMedium)
            ColSpacer(4)
            Text(
                verdict(pct),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColSpacer(18)
            Row(Modifier.fillMaxWidth()) {
                StatTile("$pct%", "Score", Modifier.weight(1f), accuracyColor(pct))
                RowSpacer(10)
                StatTile(
                    "${session.correctCount}/${session.cards.size}",
                    "Correct",
                    Modifier.weight(1f),
                    Cyan
                )
            }

            // Per-topic breakdown of this quiz, so the retry has a target
            val byTopic = session.cards.indices.groupBy { session.cards[it].topicName }
            ColSpacer(18)
            SectionTitle("BY TOPIC")
            ColSpacer(8)
            byTopic.forEach { (topic, idx) ->
                val right = idx.count { session.chosen[it] == session.cards[it].answer }
                val tp = (100.0 * right / idx.size).roundToInt()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(topic, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "$right/${idx.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = accuracyColor(tp)
                    )
                }
            }

            // Review the ones that were missed
            val missed = session.cards.indices.filter { session.chosen[it] != session.cards[it].answer }
            if (missed.isNotEmpty()) {
                ColSpacer(18)
                SectionTitle("WHAT YOU MISSED")
                ColSpacer(8)
                missed.forEach { i ->
                    val c = session.cards[i]
                    Panel(Modifier.fillMaxWidth()) {
                        Text(c.question, style = MaterialTheme.typography.titleMedium)
                        ColSpacer(8)
                        Text(
                            "Correct: ${c.options[c.answer]}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Good
                        )
                        ColSpacer(6)
                        Text(
                            c.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ColSpacer(8)
                }
            }

            ColSpacer(10)
            PrimaryButton("New quiz", { vm.startQuiz(session.cards.size) }, Modifier.fillMaxWidth())
            ColSpacer(8)
            OutlineButton("Back to home", {
                vm.endQuiz()
                onExit()
            }, Modifier.fillMaxWidth())
            ColSpacer(24)
        }
        return
    }

    val card = session.current
    if (card == null) {
        EmptyState("No questions", "Adjust your topic focus and try again.")
        return
    }
    val picked = session.chosen[session.index]

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${session.index + 1} / ${session.cards.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            RowSpacer(10)
            Bar(
                fraction = session.index.toFloat() / session.cards.size,
                modifier = Modifier.weight(1f),
                height = 6
            )
            RowSpacer(10)
            Text(
                "${session.correctCount} right",
                style = MaterialTheme.typography.labelLarge,
                color = Good
            )
        }

        ColSpacer(14)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(card.topicName, Cyan)
            RowSpacer(6)
            Tag(card.difficulty.label, difficultyColor(card.difficulty))
        }

        ColSpacer(12)
        Text(card.question, style = MaterialTheme.typography.titleLarge)
        ColSpacer(16)

        card.options.forEachIndexed { i, option ->
            OptionRow(
                text = option,
                index = i,
                selected = picked == i,
                correctIndex = card.answer,
                revealed = session.locked,
                onClick = { vm.answerQuiz(i) }
            )
            ColSpacer(8)
        }

        if (session.locked) {
            ColSpacer(8)
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle(if (picked == card.answer) "CORRECT" else "NOT QUITE")
                ColSpacer(6)
                Text(card.explanation, style = MaterialTheme.typography.bodyMedium)
                ColSpacer(12)
                SectionTitle("HOW TO SAY IT OUT LOUD")
                ColSpacer(6)
                Text(
                    card.modelAnswer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ColSpacer(12)
            PrimaryButton(
                if (session.index + 1 >= session.cards.size) "See results" else "Next question",
                { vm.nextQuiz() },
                Modifier.fillMaxWidth()
            )
        }

        ColSpacer(24)
    }
}

@Composable
private fun OptionRow(
    text: String,
    index: Int,
    selected: Boolean,
    correctIndex: Int,
    revealed: Boolean,
    onClick: () -> Unit
) {
    val isCorrect = index == correctIndex
    val shape = RoundedCornerShape(14.dp)

    val borderColor = when {
        revealed && isCorrect -> Good
        revealed && selected -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val bg = when {
        revealed && isCorrect -> Good.copy(alpha = 0.14f)
        revealed && selected -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = !revealed) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(borderColor.copy(alpha = 0.2f))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                ('A' + index).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = borderColor,
                fontWeight = FontWeight.Bold
            )
        }
        RowSpacer(12)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private fun verdict(pct: Int): String = when {
    pct >= 90 -> "Interview ready on this material."
    pct >= 75 -> "Solid. Tighten the misses below."
    pct >= 55 -> "Getting there. Re-read the explanations you missed."
    else -> "Worth reading the topic lesson before drilling again."
}
